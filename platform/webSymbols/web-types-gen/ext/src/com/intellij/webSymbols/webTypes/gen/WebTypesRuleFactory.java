package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.*;
import org.apache.commons.lang3.tuple.Pair;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.util.ParcelableHelper;
import org.jsonschema2pojo.util.ReflectionHelper;

import java.util.*;

public class WebTypesRuleFactory extends RuleFactory {

  private final WebTypesReflectionHelper myReflectionHelper = new WebTypesReflectionHelper(this);
  private final WebTypesSchemaStore mySchemaStore = new WebTypesSchemaStore();

  private final Map<String, List<Pair<JsonNode, JType>>> typeCache = new HashMap<>();
  private final Map<String, List<Pair<Set<String>, JType>>> primitiveTypeCache = new HashMap<>();

  @Override
  public WebTypesSchemaStore getSchemaStore() {
    return mySchemaStore;
  }

  @Override
  public Rule<JClassContainer, JType> getSchemaRule() {
    return new WebTypesSchemaRule(this);
  }

  @Override
  public Rule<JClassContainer, JType> getTypeRule() {
    return new WebTypesTypeRule(this);
  }

  @Override
  public Rule<JDefinedClass, JDefinedClass> getPropertiesRule() {
    return new WebTypesPropertiesRule(this);
  }

  public Rule<JDefinedClass, JDefinedClass> getPatternPropertiesRule() {
    return new WebTypesPatternPropertiesRule(this);
  }

  @Override
  public Rule<JPackage, JClass> getArrayRule() {
    return new WebTypesArrayRule(this);
  }

  @Override
  public Rule<JPackage, JType> getObjectRule() {
    return new WebTypesObjectRule(this, new ParcelableHelper(), this.myReflectionHelper);
  }

  @Override
  public ReflectionHelper getReflectionHelper() {
    return myReflectionHelper;
  }

  public Rule<JPackage, JType> getComplexTypeRule() {
    return new WebTypesComplexTypeRule(this);
  }

  public JType getTypeFromCache(String nodeName, JsonNode node) {
    var fromCache = typeCache.get(nodeName);
    if (fromCache != null) {
      var type = fromCache.stream().filter(it -> it.getKey().equals(node)).map(it -> it.getValue()).findFirst().orElse(null);
      if (type != null) {
        return type;
      }
    }
    return null;
  }

  public void storeTypeInCache(String nodeName, JsonNode node, JType type) {
    typeCache.computeIfAbsent(nodeName, it -> new ArrayList<>()).add(Pair.of(node, type));
  }

  public JType getPrimitiveTypeFromCache(String nodeName, Set<String> types) {
    var fromCache = primitiveTypeCache.get(nodeName);
    if (fromCache != null) {
      var type = fromCache.stream().filter(it -> it.getKey().equals(types)).map(it -> it.getValue()).findFirst().orElse(null);
      if (type != null) {
        return type;
      }
    }
    return null;
  }

  public void storePrimitiveTypeInCache(String nodeName, Set<String> types, JType type) {
    primitiveTypeCache.computeIfAbsent(nodeName, it -> new ArrayList<>()).add(Pair.of(types, type));
  }

  public Schema resolveSchemaRef(Schema schema, String refPath) {
    if (schema.getId().getFragment() == null) {
      refPath = "#" + refPath;
    }
    else {
      refPath = "#" + schema.getId().getFragment() + "/" + refPath;
    }
    return getSchemaStore().create(schema, refPath, getGenerationConfig().getRefFragmentPathDelimiters());
  }

}
