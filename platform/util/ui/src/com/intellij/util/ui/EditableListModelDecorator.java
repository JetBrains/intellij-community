// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import javax.swing.*;

/**
 * @author yole
 */
public class EditableListModelDecorator implements EditableModel {
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
