package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class PredefinedTargetChooser implements TargetChooser {
  private final IDevice[] myDevices;

  public PredefinedTargetChooser(@NotNull IDevice[] devices) {
    myDevices = devices;
  }

  @NotNull
  public IDevice[] getDevices() {
    return myDevices;
  }
}
