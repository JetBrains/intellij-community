// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import javax.swing.*;


public final class EditableListModelDecorator implements EditableModel {
  private final DefaultListModel myBaseModel;

  public EditableListModelDecorator(DefaultListModel model) {
    myBaseModel = model;
  }

  @Override
  public void addRow() {
  }

  @Override
  public void removeRow(int index) {
    myBaseModel.removeElementAt(index);
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {
    Object elementToMove = myBaseModel.getElementAt(oldIndex);
    if (newIndex > oldIndex) {
      newIndex--;
    }
    myBaseModel.removeElementAt(oldIndex);
    myBaseModel.insertElementAt(elementToMove, newIndex);
  }

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    return true;
  }
}
