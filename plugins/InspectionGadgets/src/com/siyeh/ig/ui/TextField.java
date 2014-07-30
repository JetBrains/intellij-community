/*
 * Copyright 2010 Bas Leijdekkers
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
package com.siyeh.ig.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class TextField extends JTextField {

  public TextField(@NotNull InspectionProfileEntry owner,
                   @NonNls String property) {
    super(getPropertyValue(owner, property));
    final DocumentListener documentListener =
      new TextFieldDocumentListener(owner, property);
    getDocument().addDocumentListener(documentListener);
  }

  private static String getPropertyValue(InspectionProfileEntry owner,
                                         String property) {
    return ReflectionUtil.getField(owner.getClass(), owner, String.class, property);
  }

  private static void setPropertyValue(InspectionProfileEntry owner,
                                       String property,
                                       String value) {
    ReflectionUtil.setField(owner.getClass(), owner, String.class, property, value);
  }

  private class TextFieldDocumentListener implements DocumentListener {

    private final InspectionProfileEntry owner;
    private final String property;

    public TextFieldDocumentListener(InspectionProfileEntry owner,
                                     String property) {
      this.owner = owner;
      this.property = property;
    }

    @Override
    public void insertUpdate(DocumentEvent documentEvent) {
      textChanged();
    }

    @Override
    public void removeUpdate(DocumentEvent documentEvent) {
      textChanged();
    }

    @Override
    public void changedUpdate(DocumentEvent documentEvent) {
      textChanged();
    }

    private void textChanged() {
      setPropertyValue(owner, property, getText());
    }
  }
}
