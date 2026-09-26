// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public final class ReplacementVariableDefinition extends NamedScriptableDefinition {
  public ReplacementVariableDefinition() {}

  public ReplacementVariableDefinition(@NotNull String name) {
    setName(name);
  }

  public ReplacementVariableDefinition(@NotNull ReplacementVariableDefinition definition) {
    super(definition);
  }

  @Override
  public ReplacementVariableDefinition copy() {
    return new ReplacementVariableDefinition(this);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ReplacementVariableDefinition && super.equals(o);
  }
}