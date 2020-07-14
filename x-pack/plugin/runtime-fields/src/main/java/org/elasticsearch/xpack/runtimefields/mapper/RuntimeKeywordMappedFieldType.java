/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.runtimefields.mapper;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.common.xcontent.ToXContent.Params;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.TextSearchInfo;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.script.Script;
import org.elasticsearch.xpack.runtimefields.StringScriptFieldScript;
import org.elasticsearch.xpack.runtimefields.fielddata.ScriptBinaryFieldData;
import org.elasticsearch.xpack.runtimefields.query.StringScriptFieldExistsQuery;
import org.elasticsearch.xpack.runtimefields.query.StringScriptFieldTermQuery;
import org.elasticsearch.xpack.runtimefields.query.StringScriptFieldTermsQuery;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public final class RuntimeKeywordMappedFieldType extends MappedFieldType {

    private final Script script;
    private final StringScriptFieldScript.Factory scriptFactory;

    RuntimeKeywordMappedFieldType(String name, Script script, StringScriptFieldScript.Factory scriptFactory, Map<String, String> meta) {
        super(name, false, false, TextSearchInfo.NONE, meta);
        this.script = script;
        this.scriptFactory = scriptFactory;
    }

    @Override
    public Object valueForDisplay(Object value) {
        if (value == null) {
            return null;
        }
        // keywords are internally stored as utf8 bytes
        BytesRef binaryValue = (BytesRef) value;
        return binaryValue.utf8ToString();
    }

    @Override
    public String typeName() {
        // TODO not sure what we should return here: the runtime type or the field type?
        // why is the same string returned from three different methods?
        return ScriptFieldMapper.CONTENT_TYPE;
    }

    @Override
    public ScriptBinaryFieldData.Builder fielddataBuilder(String fullyQualifiedIndexName) {
        // TODO once we get SearchLookup as an argument, we can already call scriptFactory.newFactory here and pass through the result
        return new ScriptBinaryFieldData.Builder(scriptFactory);
    }

    private StringScriptFieldScript.LeafFactory leafFactory(QueryShardContext context) {
        return scriptFactory.newFactory(script.getParams(), context.lookup());
    }

    @Override
    public Query existsQuery(QueryShardContext context) {
        return new StringScriptFieldExistsQuery(leafFactory(context), name());
    }

    @Override
    public Query termQuery(Object value, QueryShardContext context) {
        return new StringScriptFieldTermQuery(leafFactory(context), name(), BytesRefs.toString(Objects.requireNonNull(value)));
    }

    @Override
    public Query termsQuery(List<?> values, QueryShardContext context) {
        Set<String> terms = values.stream().map(v -> BytesRefs.toString(Objects.requireNonNull(v))).collect(toSet());
        return new StringScriptFieldTermsQuery(leafFactory(context), name(), terms);
    }

    void doXContentBody(XContentBuilder builder, boolean includeDefaults, Params params) throws IOException {
        builder.field("runtime_type", "keyword");
        builder.field("script", script.getIdOrCode()); // TODO For some reason this doesn't allow us to do the full xcontent of the script.
    }
}
