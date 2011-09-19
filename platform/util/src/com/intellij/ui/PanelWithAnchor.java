package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author evgeny.zakrevsky
 */

public interface PanelWithAnchor {
  JComponent getAnchor();
  void setAnchor(@Nullable JComponent anchor);
}
