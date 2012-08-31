package org.jetbrains.jps.eclipse.model;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;

/**
 * @author nik
 */
public class JpsEclipseClasspathSerializer extends JpsModuleClasspathSerializer {
  @NonNls public static final String CLASSPATH_STORAGE_ID = "eclipse";

  public JpsEclipseClasspathSerializer() {
    super(CLASSPATH_STORAGE_ID);
  }

  @Override
  public void loadClasspath(@NotNull JpsModule module, @Nullable String classpathDir, @NotNull String baseModulePath) {
  }
}
