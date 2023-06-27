// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalTreeUI;
import java.awt.event.MouseEvent;

public final class BegTreeUI extends MetalTreeUI {
  /* Invoked by reflection */
  public static ComponentUI createUI(JComponent c) {
    return new BegTreeUI();
  }

  @Override
  protected boolean isToggleSelectionEvent(MouseEvent e) {
    return SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && !e.isPopupTrigger();
  }
}