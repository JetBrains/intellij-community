// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

@ApiStatus.Internal
public class CollapsibleGroupedListSelectionModel extends DefaultListSelectionModel {

  private final ListModel<? extends AnAction> model;

  public CollapsibleGroupedListSelectionModel(ListModel<? extends AnAction> model) {
    setSelectionMode(SINGLE_SELECTION);
    this.model = model;
  }

  private int getNotGroupIndexInDirection(int startIndex, int direction) {
    for (int i = startIndex + direction; i >= 0 && i < model.getSize(); i += direction) {
      if (!(model.getElementAt(i) instanceof ActionGroup)) {
        return i;
      }
    }
    return startIndex;
  }

  @Override
  public void setSelectionInterval(int index0, int index1) {
    if (model.getElementAt(index0) instanceof ActionGroup) {
      int candidate = getNotGroupIndexInDirection(getMaxSelectionIndex(), index0 - getMaxSelectionIndex());
      if (candidate != getMaxSelectionIndex()) {
        super.setSelectionInterval(candidate, candidate);
      }
      return;
    }

    super.setSelectionInterval(index0, index1);
  }

  @Override
  public boolean isSelectedIndex(int index) {
    if (model.getElementAt(index) instanceof ActionGroup) {
      return false;
    }
    return super.isSelectedIndex(index);
  }
}