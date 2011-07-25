package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
class LayoutDeviceConfiguration {
  private final String myName;
  private final FolderConfiguration myConfiguration;

  LayoutDeviceConfiguration(@NotNull String name, @NotNull FolderConfiguration configuration) {
    myName = name;
    myConfiguration = configuration;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public FolderConfiguration getConfiguration() {
    return myConfiguration;
  }
}

