// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;

final class LightTestFixtureBuilderImpl<F extends IdeaProjectTestFixture> implements TestFixtureBuilder<F> {
  @NotNull
  private final F myFixture;

  LightTestFixtureBuilderImpl(@NotNull F fixture) {
    myFixture = fixture;
  }

  @NotNull
  @Override
  public F getFixture() {
    return myFixture;
  }

  @Override
  public <M extends ModuleFixtureBuilder<?>> M addModule(@NotNull Class<M> builderClass) {
    throw new UnsupportedOperationException("addModule is not allowed in : " + getClass());
  }
}
