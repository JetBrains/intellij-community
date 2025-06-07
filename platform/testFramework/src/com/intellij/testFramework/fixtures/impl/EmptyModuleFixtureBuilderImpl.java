// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;


public abstract class EmptyModuleFixtureBuilderImpl<T extends ModuleFixture> extends ModuleFixtureBuilderImpl<T> implements EmptyModuleFixtureBuilder<T> {
  public EmptyModuleFixtureBuilderImpl(@NotNull TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(ModuleType.EMPTY, fixtureBuilder);
  }

  @Override
  protected @NotNull T instantiateFixture() {
    throw new UnsupportedOperationException();
  }
}
