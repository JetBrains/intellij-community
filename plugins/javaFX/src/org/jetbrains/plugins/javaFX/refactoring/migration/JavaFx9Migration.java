// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.refactoring.migration;

import com.intellij.refactoring.migration.PredefinedMigrationProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import java.net.URL;

/**
 * @author Pavel.Dolgov
 */
public class JavaFx9Migration implements PredefinedMigrationProvider {
  @NotNull
  @Override
  public URL getMigrationMap() {
    return JavaFx9Migration.class.getResource("JavaFx8__9.xml");
  }

  @Override
  public @Nls String getDescription() {
    return JavaFXBundle.message("javafx.migration.description");
  }
}
