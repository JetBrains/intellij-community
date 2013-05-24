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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.lang.reflect.Field;

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
    try {
      final Class<? extends InspectionProfileEntry> aClass =
        owner.getClass();
      final Field field = aClass.getField(property);
      return (String)field.get(owner);
    }
    catch (IllegalAccessException ignore) {
      return null;
    }
    catch (NoSuchFieldException ignore) {
      return null;
    }
  }

  private static void setPropertyValue(InspectionProfileEntry owner,
                                       String property,
                                       String value) {
    try {
      final Class<? extends InspectionProfileEntry> aClass =
        owner.getClass();
      final Field field = aClass.getField(property);
      field.set(owner, value);
    }
    catch (IllegalAccessException ignore) {
      // do nothing
    }
    catch (NoSuchFieldException ignore) {
      // do nothing
    }
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
