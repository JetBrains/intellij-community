// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.refactoring.migration;

import com.intellij.refactoring.migration.PredefinedMigrationProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import java.net.URL;

public final class JavaFx9Migration implements PredefinedMigrationProvider {
  @Override
  public @NotNull URL getMigrationMap() {
    return JavaFx9Migration.class.getResource("JavaFx8__9.xml");
  }

  @Override
  public @Nls String getDescription() {
    return JavaFXBundle.message("javafx.migration.description");
  }
}
