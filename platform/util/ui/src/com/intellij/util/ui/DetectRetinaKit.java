// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Utility class for retina routine
 */
@ApiStatus.Internal
public final class DetectRetinaKit {
  private static final Map<GraphicsDevice, Boolean> devicesToRetinaSupportCacheMap = ContainerUtil.createWeakMap();

  @NotNull
  // cannot be static because logging maybe not configured yet
  private static Logger getLogger() {
    return Logger.getInstance("#com.intellij.util.ui.DetectRetinaKit");
  }

  /**
   * The best way to understand whether we are on a retina device is [NSScreen backingScaleFactor]
   * But we should not invoke it from any thread. We do not have access to the AppKit thread
   * on the other hand. So let's use a dedicated method. It is rather safe because it caches a
   * value that has been got on AppKit previously.
   */
  public static boolean isOracleMacRetinaDevice(GraphicsDevice device) {
    Boolean isRetina  = devicesToRetinaSupportCacheMap.get(device);

    if (isRetina != null) {
      return isRetina;
    }

    Method getScaleFactorMethod = null;
    try {
      getScaleFactorMethod = Class.forName("sun.awt.CGraphicsDevice").getMethod("getScaleFactor");
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      // not an Oracle Mac JDK or API has been changed
      getLogger().debug("CGraphicsDevice.getScaleFactor(): not an Oracle Mac JDK or API has been changed");
    }
    catch (Exception e) {
      getLogger().debug(e);
      getLogger().debug("CGraphicsDevice.getScaleFactor(): probably it is Java 9");
    }

    try {
      isRetina =  getScaleFactorMethod == null || (Integer)getScaleFactorMethod.invoke(device) != 1;
    } catch (IllegalAccessException e) {
      getLogger().debug("CGraphicsDevice.getScaleFactor(): Access issue");
      isRetina = false;
    } catch (InvocationTargetException e) {
      getLogger().debug("CGraphicsDevice.getScaleFactor(): Invocation issue");
      isRetina = false;
    } catch (IllegalArgumentException e) {
      getLogger().debug("object is not an instance of declaring class: " + device.getClass().getName());
      isRetina = false;
    }

    devicesToRetinaSupportCacheMap.put(device, isRetina);

    return isRetina;
  }

  /**
   * This method perfectly detects retina Graphics2D for jdk7+
   * @param g graphics to be tested
   * @return false if the device of the Graphics2D is not a retina device,
   * jdk is an Apple JDK or Oracle API has been changed.
   */
  static boolean isMacRetina(@NotNull Graphics2D g) {
    GraphicsConfiguration configuration = g.getDeviceConfiguration();
    if (configuration == null) {
      return false;
    }

    GraphicsDevice device = configuration.getDevice();
    return isOracleMacRetinaDevice(device);
  }

  /**
   * Checks that at least one retina device is present.
   * Do not use this method if your are going to make decision for a particular screen.
   * isRetina(Graphics2D) is more preferable
   *
   * @return true if at least one device is a retina device
   */
  static boolean isRetina() {
    // Oracle JDK

    if (SystemInfo.isMac) {
      GraphicsEnvironment e
        = GraphicsEnvironment.getLocalGraphicsEnvironment();

      GraphicsDevice[] devices = e.getScreenDevices();

      //now get the configurations for each device
      for (GraphicsDevice device : devices) {
        if (isOracleMacRetinaDevice(device)) {
          return true;
        }
      }
    }

    return false;
  }
}
