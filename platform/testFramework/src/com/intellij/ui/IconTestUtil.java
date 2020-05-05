// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IconTestUtil {
  @Nullable
  public static String getIconPath(Icon icon) {
    icon = unwrapRetrievableIcon(icon);
    return ((IconLoader.CachedImageIcon)icon).getOriginalPath();
  }

  public static Icon unwrapRetrievableIcon(Icon icon) {
    while (icon instanceof RetrievableIcon) {
      icon = ((RetrievableIcon)icon).retrieveIcon();
    }
    return icon;
  }
}
