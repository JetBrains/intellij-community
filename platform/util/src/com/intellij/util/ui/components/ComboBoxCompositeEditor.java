/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author Denis Fokin
 */

public class ComboBoxCompositeEditor extends JComponent implements ComboBoxEditor {

  public interface EditorComponent {
    void setItem(String anObject);
    String getItem();
    void selectAll();
    void addActionListener(ActionListener l);
    void removeActionListener(ActionListener l);
    JComponent getDelegate();

  }

  private final EditorComponent[] myComponents;
  private String myItem = null;
  private int focusableComponentIndex;

  public ComboBoxCompositeEditor(final JComponent ... components) {
    assert components.length > 0;
    setLayout(new GridLayout(1, 0));
    setFocusable(false);
    myComponents = new EditorComponent[components.length];

    for (int i = 0; i < components.length; i ++) {
      final int index = i;
      myComponents[i] = new ComboBoxCompositeEditor.EditorComponent () {
        public void setItem(String anObject) {
          if (components[index] instanceof JTextField) {
            JTextField component = (JTextField)components[index];
            component.setText((anObject == null) ? "" : anObject);
          }
        }

        public String getItem() {
          if (components[index] instanceof JTextField) {
            JTextField component = (JTextField)components[index];
            return component.getText();
          }
          return "";
        }

        @Override
        public void selectAll() {
          if (components[index] instanceof JTextField) {
            JTextField component = (JTextField)components[index];
            component.selectAll();
          }
        }

        @Override
        public void addActionListener(ActionListener l) {
          if (components[index] instanceof JTextField) {
            JTextField component = (JTextField)components[index];
            component.addActionListener(l);
          }
        }

        @Override
        public void removeActionListener(ActionListener l) {
          if (components[index] instanceof JTextField) {
            JTextField component = (JTextField)components[index];
            component.removeActionListener(l);
          }
        }

        @Override
        public JComponent getDelegate() {
          return components[index];
        }
      };
    }

    focusableComponentIndex = 0;
    final JComponent component = myComponents[focusableComponentIndex].getDelegate();
    component.setBorder(null);
    component.addFocusListener(new FocusListener() {

      Component parent = null;

      @Override
      public void focusGained(FocusEvent e) {
        parent = component.getParent();
        parent.repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        parent.repaint();
      }
    });
  }

  @Override
  public Component getEditorComponent() {
    return myComponents[focusableComponentIndex].getDelegate();
  }

  @Override
  public void setItem(Object anObject) {
      myItem = (String)anObject;
      for (EditorComponent editorComponent : myComponents) {
        editorComponent.setItem(myItem);
      }
  }

  @Override
  public Object getItem() {
    return myItem;
  }

  @Override
  public void selectAll() {
    myComponents[focusableComponentIndex].selectAll();
  }

  @Override
  public void addActionListener(ActionListener l) {
    for (EditorComponent editorComponent : myComponents) {
      editorComponent.addActionListener(l);
    }
  }

  @Override
  public void removeActionListener(ActionListener l) {
    for (EditorComponent editorComponent : myComponents) {
      editorComponent.removeActionListener(l);
    }
  }

  @Override
  public void setFocusable(boolean focusable) {}

  @Override
  public boolean isFocusable() {
    return false;
  }
}
