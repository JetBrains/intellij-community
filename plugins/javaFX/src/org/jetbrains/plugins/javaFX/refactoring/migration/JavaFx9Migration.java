package org.jetbrains.plugins.javaFX.refactoring.migration;

import com.intellij.refactoring.migration.PredefinedMigrationProvider;
import org.jetbrains.annotations.NotNull;

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
}
