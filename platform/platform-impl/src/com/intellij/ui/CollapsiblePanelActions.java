// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.ui.speedSearch.SpeedSearchSupply.getSupply;
import static com.intellij.util.ObjectUtils.tryCast;

public class CollapsiblePanelActions extends SwingActionDelegate {

  private CollapsiblePanelActions(@NonNls String actionId) {
    super(actionId);
  }

  @Override
  protected @Nullable JComponent getComponent(AnActionEvent event) {
    JLabel label = tryCast(super.getComponent(event), JLabel.class);
    return label == null || getSupply(label) != null ? null : label;
  }

  public static final class Toggle extends CollapsiblePanelActions {
    public static final String ID = "toggle";

    public Toggle() {
      super(ID);
    }
  }
}
