package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.Jsonschema2Pojo;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.exception.GenerationException;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.split;

public class WebTypesSchemaRule implements Rule<JClassContainer, JType> {

  private final RuleFactory ruleFactory;

  protected WebTypesSchemaRule(RuleFactory ruleFactory) {
    this.ruleFactory = ruleFactory;
  }

  /**
   * Applies this schema rule to take the required code generation steps.
   * <p>
   * At the root of a schema document this rule should be applied (schema
   * documents contain a schema), but also in many places within the document.
   * Each property of type "object" is itself defined by a schema, the items
   * attribute of an array is a schema, the additionalProperties attribute of
   * a schema is also a schema.
   * <p>
   * Where the schema value is a $ref, the ref URI is assumed to be applicable
   * as a URL (from which content will be read). Where the ref URI has been
   * encountered before, the root Java type created by that schema will be
   * re-used (generation steps won't be repeated).
   *
   * @param schema
   *            the schema within which this schema rule is being applied
   */
  @Override
  public JType apply(String nodeName, JsonNode schemaNode, JsonNode parent, JClassContainer generatableType, Schema schema) {

    if (schemaNode.has("$ref")) {
      final String nameFromRef = nameFromRef(schemaNode.get("$ref").asText());

      schema = ruleFactory.getSchemaStore().create(schema, schemaNode.get("$ref").asText(), ruleFactory.getGenerationConfig().getRefFragmentPathDelimiters());
      schemaNode = schema.getContent();

      if (schema.isGenerated()) {
        return schema.getJavaType();
      }

      return apply(nameFromRef != null ? nameFromRef : nodeName, schemaNode, parent, generatableType, schema);
    }

    schema = schema.deriveChildSchema(schemaNode);

    JType javaType;
    if (schemaNode.has("enum")) {
      javaType = ruleFactory.getEnumRule().apply(nodeName, schemaNode, parent, generatableType, schema);
    } else {
      javaType = ruleFactory.getTypeRule().apply(nodeName, schemaNode, parent, generatableType.getPackage(), schema);
    }
    schema.setJavaTypeIfEmpty(javaType);
    if (schema.getJavaType() != javaType && javaType instanceof JDefinedClass) {
      JDefinedClass cls = (JDefinedClass)javaType;
      cls._package().remove(cls);
    }
    return schema.getJavaType();
  }

  private String nameFromRef(String ref) {

    if ("#".equals(ref)) {
      return null;
    }

    String nameFromRef;
    if (!contains(ref, "#")) {
      nameFromRef = Jsonschema2Pojo.getNodeName(ref, ruleFactory.getGenerationConfig());
    } else {
      String[] nameParts = split(ref, "/\\#");
      nameFromRef = nameParts[nameParts.length - 1];
    }

    try {
      return URLDecoder.decode(nameFromRef, "utf-8");
    } catch (UnsupportedEncodingException e) {
      throw new GenerationException("Failed to decode ref: " + ref, e);
    }
  }
}
