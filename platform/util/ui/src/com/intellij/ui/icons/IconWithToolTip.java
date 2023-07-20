// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Icon which supports providing a tooltip.
 */
public interface IconWithToolTip extends Icon {
  /**
   * Returns the tooltip for the icon.
   * @param composite if true, this tooltip will be combined with other tooltips (from other layers of a layered icon or parts of a row icon).
   *                  For some icons, it only makes sense to show a tooltip if the icon is composite.
   */
  @NlsSafe @Nullable String getToolTip(boolean composite);
}
