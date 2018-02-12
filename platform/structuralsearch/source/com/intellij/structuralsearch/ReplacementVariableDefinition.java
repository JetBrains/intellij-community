// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

/**
 * @author Maxim.Mossienko
 */
public class ReplacementVariableDefinition extends NamedScriptableDefinition {
  public ReplacementVariableDefinition() {}

  public ReplacementVariableDefinition(ReplacementVariableDefinition definition) {
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