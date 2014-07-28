package com.intellij.structuralsearch.impl.matcher;

import com.intellij.structuralsearch.impl.matcher.strategies.XmlMatchingStrategy;

/**
* @author Eugene.Kudelevsky
*/
public class XmlCompiledPattern extends CompiledPattern {
  private static final String XML_TYPED_VAR_PREFIX = "__";

  public XmlCompiledPattern() {
    setStrategy(XmlMatchingStrategy.getInstance());
  }

  public String[] getTypedVarPrefixes() {
    return new String[] {XML_TYPED_VAR_PREFIX};
  }

  public boolean isTypedVar(final String str) {
    return str.startsWith(XML_TYPED_VAR_PREFIX);
  }
}
