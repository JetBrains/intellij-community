// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author evgeny.zakrevsky
 */

public interface PanelWithAnchor {
  JComponent getAnchor();
  void setAnchor(@Nullable JComponent anchor);
  default @Nullable JComponent getOwnAnchor() { return getAnchor(); }
}
