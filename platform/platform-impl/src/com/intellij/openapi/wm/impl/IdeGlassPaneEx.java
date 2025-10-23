// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.IdeGlassPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public interface IdeGlassPaneEx extends IdeGlassPane {
  Component add(Component comp);

  void remove(Component comp);

  int getComponentCount();
  Component getComponent(int index);

  boolean isInModalContext();

  boolean isColorfulToolbar();

  @NotNull JRootPane getRootPane();
}
