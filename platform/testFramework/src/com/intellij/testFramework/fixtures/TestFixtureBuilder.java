// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import org.jetbrains.annotations.NotNull;

public interface TestFixtureBuilder<T extends IdeaTestFixture> {
  @NotNull
  T getFixture();

  <M extends ModuleFixtureBuilder<?>> M addModule(@NotNull Class<M> builderClass);
}
