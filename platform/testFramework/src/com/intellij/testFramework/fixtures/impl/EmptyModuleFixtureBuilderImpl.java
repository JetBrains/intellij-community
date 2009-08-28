package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

/**
 * @author yole
 */
public abstract class EmptyModuleFixtureBuilderImpl<T extends ModuleFixture> extends ModuleFixtureBuilderImpl<T> implements EmptyModuleFixtureBuilder<T> {
  public EmptyModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(ModuleType.EMPTY, fixtureBuilder);
  }

  protected T instantiateFixture() {
    throw new UnsupportedOperationException();
  }
}
