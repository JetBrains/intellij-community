// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ui.icons.ReplaceableIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * An icon wrapping and painting another icon.
 */
public interface RetrievableIcon extends Icon, ReplaceableIcon {
  /**
   * Returns the wrapped icon.
   */
  @NotNull Icon retrieveIcon();

  @ApiStatus.Internal
  default boolean isComplex() {
    return false;
  }
}