package org.jetbrains.jps.android.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.JpsAndroidDexCompilerConfiguration;
import org.jetbrains.jps.android.model.JpsAndroidExtensionService;
import org.jetbrains.jps.model.JpsProject;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsAndroidExtensionServiceImpl extends JpsAndroidExtensionService {
  @Nullable
  @Override
  public JpsAndroidDexCompilerConfiguration getDexCompilerConfiguration(@NotNull JpsProject project) {
    final JpsAndroidDexCompilerConfiguration config = project.getContainer().getChild(JpsAndroidDexCompilerConfigurationImpl.ROLE);
    return config != null ? config : new JpsAndroidDexCompilerConfigurationImpl();
  }

  @Override
  public void setDexCompilerConfiguration(@NotNull JpsProject project, @NotNull JpsAndroidDexCompilerConfiguration configuration) {
    project.getContainer().setChild(JpsAndroidDexCompilerConfigurationImpl.ROLE, configuration);
  }
}
