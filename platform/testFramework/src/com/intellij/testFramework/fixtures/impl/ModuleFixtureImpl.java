// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.Module;
import com.intellij.testFramework.fixtures.ModuleFixture;
import org.jetbrains.annotations.NotNull;

public class ModuleFixtureImpl extends BaseFixture implements ModuleFixture {
  private Module myModule;
  protected final ModuleFixtureBuilderImpl myBuilder;

  public ModuleFixtureImpl(@NotNull ModuleFixtureBuilderImpl builder) {
    myBuilder = builder;
  }

  @Override
  public @NotNull Module getModule() {
    if (myModule == null) {
      myModule = myBuilder.buildModule();
    }
    return myModule;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getModule();
  }

  @Override
  public void tearDown() throws Exception {
    myModule = null;
    super.tearDown();
  }
}
