// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher;

import org.jetbrains.annotations.NotNull;

public class XmlCompiledPattern extends CompiledPattern {
  private static final String XML_TYPED_VAR_PREFIX = "__";

  public XmlCompiledPattern() {}

  @Override
  public String @NotNull [] getTypedVarPrefixes() {
    return new String[] {XML_TYPED_VAR_PREFIX};
  }

  @Override
  public boolean isTypedVar(final @NotNull String str) {
    return str.trim().startsWith(XML_TYPED_VAR_PREFIX);
  }
}
