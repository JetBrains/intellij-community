/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
