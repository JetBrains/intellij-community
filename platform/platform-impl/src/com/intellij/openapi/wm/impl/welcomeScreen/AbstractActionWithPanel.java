// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class AbstractActionWithPanel extends AnAction implements DumbAware, Disposable {
  public abstract JPanel createPanel();

  public void onPanelSelected() {}

  public abstract @NotNull JButton getActionButton();
}
