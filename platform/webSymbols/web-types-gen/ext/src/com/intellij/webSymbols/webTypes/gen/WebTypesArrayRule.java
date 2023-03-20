package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.util.Inflector;

import java.util.List;
import java.util.Set;

public class WebTypesArrayRule implements Rule<JPackage, JClass> {

    private final WebTypesRuleFactory ruleFactory;

    protected WebTypesArrayRule(WebTypesRuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
    }

    @Override
    public JClass apply(String nodeName, JsonNode node, JsonNode parent, JPackage jpackage, Schema schema) {

        boolean uniqueItems = node.has("uniqueItems") && node.get("uniqueItems").asBoolean();
        boolean rootSchemaIsArray = !schema.isGenerated();

        JType itemType;
        if (node.has("items")) {
            itemType = ruleFactory.getSchemaRule().apply(makeSingular(nodeName), node.get("items"), node, jpackage,
                                                         ruleFactory.resolveSchemaRef(schema, "items"));
        } else {
            itemType = jpackage.owner().ref(Object.class);
        }

        JClass arrayType;
        if (uniqueItems) {
            arrayType = jpackage.owner().ref(Set.class).narrow(itemType);
        } else {
            arrayType = jpackage.owner().ref(List.class).narrow(itemType);
        }

        if (rootSchemaIsArray) {
            schema.setJavaType(arrayType);
        }

        return arrayType;
    }

    private static String makeSingular(String nodeName) {
        return Inflector.getInstance().singularize(nodeName);
    }

}
