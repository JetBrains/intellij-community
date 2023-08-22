package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.codemodel.*;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.Rule;

import java.util.Iterator;

public class WebTypesPropertiesRule implements Rule<JDefinedClass, JDefinedClass> {

    private final WebTypesRuleFactory ruleFactory;

    protected WebTypesPropertiesRule(WebTypesRuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
    }

    @Override
    public JDefinedClass apply(String nodeName, JsonNode node, JsonNode parent, JDefinedClass jclass, Schema schema) {
        if (node == null) {
            node = JsonNodeFactory.instance.objectNode();
        }

        for (Iterator<String> properties = node.fieldNames(); properties.hasNext(); ) {
            String property = properties.next();

            ruleFactory.getPropertyRule().apply(property, node.get(property), node, jclass,
                                                ruleFactory.resolveSchemaRef(schema, "properties/" + property));
        }

        if (ruleFactory.getGenerationConfig().isGenerateBuilders() && !jclass._extends().name().equals("Object")) {
            addOverrideBuilders(jclass, jclass.owner()._getClass(jclass._extends().fullName()));
        }

        ruleFactory.getAnnotator().propertyOrder(jclass, node);

        return jclass;
    }

    private void addOverrideBuilders(JDefinedClass jclass, JDefinedClass parentJclass) {
        if (parentJclass == null) {
            return;
        }

        for (JMethod parentJMethod : parentJclass.methods()) {
            if (parentJMethod.name().startsWith("with") && parentJMethod.params().size() == 1) {
                addOverrideBuilder(jclass, parentJMethod, parentJMethod.params().get(0));
            }
        }
    }

    private void addOverrideBuilder(JDefinedClass thisJDefinedClass, JMethod parentBuilder, JVar parentParam) {

        // Confirm that this class doesn't already have a builder method matching the same name as the parentBuilder
        if (thisJDefinedClass.getMethod(parentBuilder.name(), new JType[] {parentParam.type()}) == null) {

            JMethod builder = thisJDefinedClass.method(parentBuilder.mods().getValue(), thisJDefinedClass, parentBuilder.name());
            builder.annotate(Override.class);

            JVar param = builder.param(parentParam.type(), parentParam.name());
            JBlock body = builder.body();
            body.invoke(JExpr._super(), parentBuilder).arg(param);
            body._return(JExpr._this());

        }
    }
}
