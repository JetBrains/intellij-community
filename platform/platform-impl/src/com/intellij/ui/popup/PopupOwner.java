// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface PopupOwner {
  @Nullable
  Point getBestPopupPosition();
  default @Nullable JComponent getPopupComponent() {
    return null;
  }
}
