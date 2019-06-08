// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.FileInputStream;
import java.io.InputStream;

public class MacColorSpaceLoader {
  private static final String GENERIC_RGB_PROFILE_PATH = "/System/Library/ColorSync/Profiles/Generic RGB Profile.icc";

  private static final ColorSpace ourGenericRgbColorSpace;
  static {
    ICC_ColorSpace colorSpace = null;
    if (SystemInfo.isMac) {
      try (InputStream is = new FileInputStream(GENERIC_RGB_PROFILE_PATH)) {
        ICC_Profile profile = ICC_Profile.getInstance(is);
        colorSpace = new ICC_ColorSpace(profile);
      }
      catch (Throwable e) {
        Logger.getInstance(MacColorSpaceLoader.class).warn("Couldn't load generic RGB color profile", e);
      }
    }
    ourGenericRgbColorSpace = colorSpace;
  }

  public static @Nullable ColorSpace getGenericRgbColorSpace() {
    return ourGenericRgbColorSpace;
  }
}