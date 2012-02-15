package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class LayoutDeviceConfiguration {
  private LayoutDevice myDevice;
  private final String myName;
  private final FolderConfiguration myConfiguration;

  LayoutDeviceConfiguration(@NotNull LayoutDevice device, @NotNull String name, @NotNull FolderConfiguration configuration) {
    myDevice = device;
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

  @NotNull
  public LayoutDevice getDevice() {
    return myDevice;
  }

  public void setDevice(LayoutDevice device) {
    myDevice = device;
  }
}

