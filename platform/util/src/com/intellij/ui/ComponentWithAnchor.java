package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author evgeny.zakrevsky
 */

public interface ComponentWithAnchor {
  JComponent getAnchor();
  void setAnchor(@Nullable JComponent anchor);
}
