// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.psi.PsiElement;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public final class JBListUpdater implements ListComponentUpdater {
  private final JBList myComponent;

  public JBListUpdater(JBList component) {
    myComponent = component;
  }

  @Override
  public void paintBusy(final boolean paintBusy) {
    final Runnable runnable = () -> myComponent.setPaintBusy(paintBusy);
    //ensure start/end order
    SwingUtilities.invokeLater(runnable);
  }

  public JBList getJBList() {
    return myComponent;
  }

  @Override
  public void replaceModel(@NotNull List<? extends PsiElement> data) {
    final Object selectedValue = myComponent.getSelectedValue();
    final int index = myComponent.getSelectedIndex();
    ListModel model = myComponent.getModel();
    if (model instanceof NameFilteringListModel) {
      ((NameFilteringListModel)model).replaceAll(data);
    } else if (model instanceof CollectionListModel){
      ((CollectionListModel)model).replaceAll(data);
    } else {
      throw new UnsupportedOperationException("JList model of class " + model.getClass() + " is not supported by JBListUpdater");
    }

    if (index == 0) {
      myComponent.setSelectedIndex(0);
    }
    else {
      myComponent.setSelectedValue(selectedValue, true);
    }
  }
}
