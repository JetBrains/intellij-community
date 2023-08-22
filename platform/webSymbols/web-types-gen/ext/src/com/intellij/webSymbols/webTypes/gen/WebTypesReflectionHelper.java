package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.util.ReflectionHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class WebTypesReflectionHelper extends ReflectionHelper {
  private final WebTypesRuleFactory ruleFactory;

  WebTypesReflectionHelper(WebTypesRuleFactory ruleFactory) {
    super(ruleFactory);
    this.ruleFactory = ruleFactory;
  }

  @Override
  public JType getSuperType(String nodeName, JsonNode node, JPackage jPackage, Schema schema) {
    List<JType> superTypes = getSuperTypesFromAllAnyOne(nodeName, node, jPackage, schema);
    if (!superTypes.isEmpty()) {
      JType result = null;
      for (JType superType : superTypes) {
        if (superType instanceof JClass && !((JClass)superType).isInterface()) {
          if (result != null) {
            ruleFactory.getLogger().error(
              "Only one of super types provided through 'anyOf' can be a class. Specify '\"x-java-interface\": true' on others to make them interfaces. Error for " +
              nodeName + " and superTypes: " + superType.name() + " and " + result.name());
            break;
          }
          result = superType;
        }
      }
      if (result != null) {
        return result;
      }
    }
    return super.getSuperType(nodeName, node, jPackage, schema);
  }

  public List<JType> getSuperTypesFromAllAnyOne(String nodeName, JsonNode node, JPackage jPackage, Schema schema) {
    if (node instanceof ObjectNode
        && !node.has("extendsJavaClass")
        && !node.has("extends")) {
      JsonNode allOf = node.get("allOf");
      JsonNode anyOf = node.get("anyOf");
      JsonNode oneOf = node.get("oneOf");
      JsonNode superRef = allOf;
      String propName = "allOf";

      if (superRef == null) {
        if (oneOf != null && anyOf == null) {
          superRef = oneOf;
          propName = "oneOf";
        }
        else if (oneOf == null && anyOf != null) {
          superRef = anyOf;
          propName = "anyOf";
        }
      }
      else if (anyOf != null || oneOf != null) {
        superRef = null;
      }

      if (superRef != null && superRef.isArray() && (superRef.size() == 1 || (superRef.size() > 1 && propName.equals("allOf")))) {
        List<JType> result = new ArrayList<>();
        for (int i = 0; i < superRef.size(); i++) {
          Schema superTypeSchema = this.ruleFactory.resolveSchemaRef(schema, propName + "/" + i);
          JType superType = this.ruleFactory.getSchemaRule().apply(
            nodeName + "Parent", superRef.get(i), node, jPackage, superTypeSchema);
          if (superType != null) {
            result.add(superType);
          }
        }
        return result;
      }
    }
    return Collections.emptyList();
  }
}