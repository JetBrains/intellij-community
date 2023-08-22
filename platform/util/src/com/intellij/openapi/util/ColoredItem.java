// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Provides background color in all Trees, Lists and ComboBoxes.
 *
 * @author gregsh
 */
public interface ColoredItem {
  @Nullable
  Color getColor();
}
