package org.jetbrains.jps.android.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class JpsAndroidExtensionService {
  public static JpsAndroidExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsAndroidExtensionService.class);
  }

  @Nullable
  public abstract JpsAndroidDexCompilerConfiguration getDexCompilerConfiguration(@NotNull JpsProject project);

  public abstract void setDexCompilerConfiguration(@NotNull JpsProject project, @NotNull JpsAndroidDexCompilerConfiguration configuration);
}
