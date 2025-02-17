// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LightTestFixtureBuilderImpl<F extends IdeaProjectTestFixture> implements TestFixtureBuilder<F> {
  private final @NotNull F myFixture;

  public LightTestFixtureBuilderImpl(@NotNull F fixture) {
    myFixture = fixture;
  }

  @Override
  public @NotNull F getFixture() {
    return myFixture;
  }

  @Override
  public <M extends ModuleFixtureBuilder<?>> M addModule(@NotNull Class<M> builderClass) {
    throw new UnsupportedOperationException("addModule is not allowed in : " + getClass());
  }
}
