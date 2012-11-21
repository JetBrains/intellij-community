package com.intellij.android.designer.profile;

import com.android.sdklib.devices.Device;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class DeviceWrapper {
  private final Device myDevice;

  public DeviceWrapper(@Nullable Device device) {
    myDevice = device;
  }

  @Nullable
  public Device getDevice() {
    return myDevice;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DeviceWrapper wrapper = (DeviceWrapper)o;

    if (myDevice != null ? !myDevice.equals(wrapper.myDevice) : wrapper.myDevice != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myDevice != null ? myDevice.hashCode() : 0;
  }

  public String getName() {
    return myDevice != null ? myDevice.getName() : "[None]";
  }
}
