package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author evgeny.zakrevsky
 */

public interface AnchorableComponent {
  @Nullable JComponent getAnchor();
  void setAnchor(@Nullable JComponent anchor);
}
