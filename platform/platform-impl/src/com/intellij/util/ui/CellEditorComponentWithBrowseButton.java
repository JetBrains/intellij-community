/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.ui.ComponentWithBrowseButton;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author dyoma
 */
public class CellEditorComponentWithBrowseButton<Comp extends JComponent> extends JPanel {
  private final ComponentWithBrowseButton<Comp> myComponent;
  private final TableCellEditor myEditor;
  private final CellEditorListener myCellEditorListener = new CellEditorListener() {
    @Override
    public void editingCanceled(ChangeEvent e) {
      onEditingFinished();
    }

    @Override
    public void editingStopped(ChangeEvent e) {
      onEditingFinished();
    }
  };
  private boolean myEditingFinished = false;

  public CellEditorComponentWithBrowseButton(ComponentWithBrowseButton<Comp> component, TableCellEditor editor) {
    super(new BorderLayout());
    myComponent = component;
    myEditor = editor;
    add(myComponent, BorderLayout.CENTER);
    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myEditor.stopCellEditing();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myEditor.cancelCellEditing();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  public ComponentWithBrowseButton<Comp> getComponentWithButton() {
    return myComponent;
  }

  public Comp getChildComponent() {
    return getComponentWithButton().getChildComponent();
  }

  @Override
  public void requestFocus() {
    myComponent.requestFocus();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void setNextFocusableComponent(Component aComponent) {
    myComponent.setNextFocusableComponent(aComponent);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    myEditingFinished = false;
    myEditor.addCellEditorListener(myCellEditorListener);
  }

  @Override
  public void removeNotify() {
    if (!myEditingFinished) {
      myEditor.stopCellEditing();
      myEditingFinished = true;
    }
    super.removeNotify();
  }

  private void onEditingFinished() {
    myEditor.removeCellEditorListener(myCellEditorListener);
    myEditingFinished = true;
  }

  private KeyEvent myCurrentEvent = null;
  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (condition == WHEN_FOCUSED && myCurrentEvent != e)
      try {
        myCurrentEvent = e;
        myComponent.getChildComponent().dispatchEvent(e);
      }
      finally {
        myCurrentEvent = null;
      }
    if (e.isConsumed()) return true;
    return super.processKeyBinding(ks, e, condition, pressed);
  }
}
