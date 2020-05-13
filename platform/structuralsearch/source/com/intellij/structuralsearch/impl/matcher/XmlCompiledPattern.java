// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.structuralsearch.impl.matcher.strategies.XmlMatchingStrategy;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class XmlCompiledPattern extends CompiledPattern {
  private static final String XML_TYPED_VAR_PREFIX = "__";

  public XmlCompiledPattern() {
    setStrategy(XmlMatchingStrategy.getInstance());
  }

  @Override
  public String @NotNull [] getTypedVarPrefixes() {
    return new String[] {XML_TYPED_VAR_PREFIX};
  }

  @Override
  public boolean isTypedVar(@NotNull final String str) {
    return str.trim().startsWith(XML_TYPED_VAR_PREFIX);
  }
}
