// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.EditorEx;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;

final class CaretPropertyChangeListener implements PropertyChangeListener {
  private final EditorImpl myEditor;
  private final Collection<CaretImpl> myCarets;

  CaretPropertyChangeListener(EditorImpl editor, Collection<CaretImpl> carets) {
    myEditor = editor;
    myCarets = carets;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (myEditor.isColumnMode()) {
      return;
    }
    if (EditorEx.PROP_COLUMN_MODE.equals(evt.getPropertyName())) {
      for (CaretImpl caret : myCarets) {
        caret.resetVirtualSelection();
      }
    }
  }
}
