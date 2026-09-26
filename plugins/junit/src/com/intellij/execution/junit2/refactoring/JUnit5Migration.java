// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.refactoring;

import com.intellij.execution.JUnitBundle;
import com.intellij.refactoring.migration.PredefinedMigrationProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.net.URL;


public final class JUnit5Migration implements PredefinedMigrationProvider {

  @Override
  public @NotNull URL getMigrationMap() {
    return JUnit5Migration.class.getResource("JUnit4__5.xml");
  }

  @Override
  public @Nls String getDescription() {
    return JUnitBundle.message("junit5.migration.description");
  }
}
