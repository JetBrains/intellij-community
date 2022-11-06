package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.codemodel.*;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.Rule;

import java.util.HashMap;
import java.util.Map;

public class WebTypesPatternPropertiesRule implements Rule<JDefinedClass, JDefinedClass> {

  private final WebTypesRuleFactory ruleFactory;

  protected WebTypesPatternPropertiesRule(WebTypesRuleFactory ruleFactory) {
    this.ruleFactory = ruleFactory;
  }

  @Override
  public JDefinedClass apply(String nodeName, JsonNode node, JsonNode parent, JDefinedClass jclass, Schema schema) {

    if (!(node instanceof ObjectNode) || node.size() != 1) {
      return jclass;
    }

    if (!this.ruleFactory.getGenerationConfig().isIncludeAdditionalProperties()) {
      // no additional properties allowed
      return jclass;
    }

    if (!ruleFactory.getAnnotator().isAdditionalPropertiesSupported()) {
      // schema allows additional properties, but serializer library can't support them
      return jclass;
    }

    ObjectNode objectNode = (ObjectNode)node;

    JsonNode propertyNode = objectNode.iterator().next();

    JType propertyType;
    if (propertyNode != null && propertyNode.size() != 0) {
      propertyType = ruleFactory.getSchemaRule().apply(nodeName + "Property", propertyNode, parent, jclass, schema);
    } else {
      propertyType = jclass.owner().ref(Object.class);
    }

    JFieldVar field = addAdditionalPropertiesField(jclass, propertyType);

    addGetter(jclass, field);

    addSetter(jclass, propertyType, field);

    if (ruleFactory.getGenerationConfig().isIncludeJsr303Annotations()) {
      ruleFactory.getValidRule().apply(nodeName, node, parent, field, schema);
    }

    return jclass;
  }

  private JFieldVar addAdditionalPropertiesField(JDefinedClass jclass, JType propertyType) {
    JClass propertiesMapType = jclass.owner().ref(Map.class);
    propertiesMapType = propertiesMapType.narrow(jclass.owner().ref(String.class), propertyType.boxify());

    JClass propertiesMapImplType = jclass.owner().ref(HashMap.class);
    propertiesMapImplType = propertiesMapImplType.narrow(jclass.owner().ref(String.class), propertyType.boxify());

    JFieldVar field = jclass.field(JMod.PRIVATE, propertiesMapType, "additionalProperties");

    ruleFactory.getAnnotator().additionalPropertiesField(field, jclass, "additionalProperties");

    field.init(JExpr._new(propertiesMapImplType));

    return field;
  }

  private void addSetter(JDefinedClass jclass, JType propertyType, JFieldVar field) {
    JMethod setter = jclass.method(JMod.PUBLIC, void.class, "setAdditionalProperty");

    ruleFactory.getAnnotator().anySetter(setter, jclass);

    JVar nameParam = setter.param(String.class, "name");
    JVar valueParam = setter.param(propertyType, "value");

    JInvocation mapInvocation = setter.body().invoke(JExpr._this().ref(field), "put");
    mapInvocation.arg(nameParam);
    mapInvocation.arg(valueParam);
  }

  private JMethod addGetter(JDefinedClass jclass, JFieldVar field) {
    JMethod getter = jclass.method(JMod.PUBLIC, field.type(), "getAdditionalProperties");

    ruleFactory.getAnnotator().anyGetter(getter, jclass);

    getter.body()._return(JExpr._this().ref(field));
    return getter;
  }

}
