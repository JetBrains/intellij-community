package com.intellij.webSymbols.webTypes.gen;

import com.sun.codemodel.*;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.util.ParcelableHelper;
import org.jsonschema2pojo.util.ReflectionHelper;

public class WebTypesRuleFactory extends RuleFactory {

  private final WebTypesReflectionHelper myReflectionHelper = new WebTypesReflectionHelper(this);
  private final WebTypesSchemaStore mySchemaStore = new WebTypesSchemaStore();

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

  public Rule<JPackage, JType> getOneOfRule() {
    return new WebTypesOneOfRule(this);
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
