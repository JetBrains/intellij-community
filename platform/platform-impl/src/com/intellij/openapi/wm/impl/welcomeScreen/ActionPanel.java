// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ActionPanel extends JPanel {
  final List<AnAction> myActions = new ArrayList<>();

  public ActionPanel(@NotNull LayoutManager layout) {
    super(layout);
  }

  public @NotNull List<AnAction> getActions() {
    return myActions;
  }

  void addAction(@NotNull AnAction action) {
    myActions.add(action);
  }
}
