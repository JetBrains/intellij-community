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
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author Denis Fokin
 */

/**
 * JComboBox<String> comboBox = new ComboBox<>(new String[] {"First", "Second", "Third"});
 * comboBox.setEditable(true);
 * comboBox.setEditor(new ComboBoxCompositeEditor(new EditorTextField(), new JLabel(AllIcons.Icon_CE)));
 * @param <ItemType>
 * @param <FocusableComponentType>
 */

public class ComboBoxCompositeEditor<ItemType, FocusableComponentType extends JComponent> extends JComponent implements ComboBoxEditor {

  public interface EditorComponent<FocusableComponentType, ItemType> {
    void setItem(ItemType anObject);
    ItemType getItem();
    void selectAll();
    void addActionListener(ActionListener l);
    void removeActionListener(ActionListener l);
    JComponent getDelegate();
  }

  public ComboBoxCompositeEditorStrategy getStrategy(FocusableComponentType focuasbleComponent) {
    ComboBoxCompositeEditorStrategy strategy = null;
    if (focuasbleComponent instanceof JTextField) {
      strategy = jTextFieldStrategy;
    } else if (focuasbleComponent instanceof EditorTextField) {
      strategy = editorTextFieldStrategy;
    }
    return strategy;
  }

  abstract class ComboBoxCompositeEditorStrategy<FocusableComponentType> {

    abstract void setItem(JComponent component, ItemType anObject);

    abstract void selectAll(JComponent component);

    abstract void addActionListener(JComponent component, ActionListener l) ;

    abstract void removeActionListener(JComponent component, ActionListener l) ;
  }

  private ComboBoxCompositeEditorStrategy editorTextFieldStrategy = new ComboBoxCompositeEditorStrategy<ItemType> () {
    public void setItem(JComponent component, ItemType anObject) {
      ((EditorTextField)component).setText((anObject == null) ? "" : anObject.toString());
    }

    public void selectAll(JComponent component) {
      ((EditorTextField)component).selectAll();
    }

    public void addActionListener(JComponent component, ActionListener l) {
      //((EditorTextField)component).addActionListener(l);
    }

    public void removeActionListener(JComponent component, ActionListener l) {
      //((EditorTextField)component).removeActionListener(l);
    }
  };

  private ComboBoxCompositeEditorStrategy jTextFieldStrategy =  new ComboBoxCompositeEditorStrategy<ItemType> (){
    public void setItem(JComponent component, ItemType anObject) {
      ((JTextField)component).setText((anObject ==null) ? "" : anObject.toString());
    }

    public void selectAll(JComponent component) {
      ((JTextField)component).selectAll();
    }

    public void addActionListener(JComponent component, ActionListener l) {
      ((JTextField)component).addActionListener(l);
    }

    public void removeActionListener(JComponent component, ActionListener l) {
      ((JTextField)component).removeActionListener(l);
    }
  };

  private final EditorComponent<FocusableComponentType, ItemType>[] myComponents;
  private ItemType myItem = null;
  private int focusableComponentIndex;

  public ComboBoxCompositeEditor(final JComponent ... components) {
    assert components.length > 0;
    setLayout(new GridLayout(1, 0));
    setFocusable(false);
    myComponents = new EditorComponent[components.length];

    for (int i = 0; i < components.length; i ++) {
      final int index = i;
      add(components[i]);
    }

    final ComboBoxCompositeEditorStrategy strategy = getStrategy((FocusableComponentType)components[focusableComponentIndex]);

    myComponents[focusableComponentIndex] = new ComboBoxCompositeEditor.EditorComponent<FocusableComponentType, ItemType>() {

      ItemType myItem;

      public void setItem(ItemType anObject) {
        myItem = anObject;
        strategy.setItem(components[focusableComponentIndex], anObject);
      }

      public ItemType getItem() {
        return myItem;
      }

      @Override
      public void selectAll() {
        strategy.selectAll(components[focusableComponentIndex]);
      }

      @Override
      public void addActionListener(ActionListener l) {
        strategy.addActionListener(components[focusableComponentIndex], l);
      }

      @Override
      public void removeActionListener(ActionListener l) {
        strategy.removeActionListener(components[focusableComponentIndex], l);
      }

      @Override
      public JComponent getDelegate() {
        return components[focusableComponentIndex];
      }
    };


    invalidate();

    focusableComponentIndex = 0;
    final JComponent component = myComponents[focusableComponentIndex].getDelegate();
    component.setBorder(null);
    component.addFocusListener(new FocusListener() {

      Component parent = null;

      @Override
      public void focusGained(FocusEvent e) {
        parent = getParent();
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
    return this;
  }

  @Override
  public void setItem(Object anObject) {
    myComponents[focusableComponentIndex].setItem((ItemType)anObject);
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
    myComponents[focusableComponentIndex].addActionListener(l);
  }

  @Override
  public void removeActionListener(ActionListener l) {
    myComponents[focusableComponentIndex].removeActionListener(l);
  }

  @Override
  public void setFocusable(boolean focusable) {}

  @Override
  public boolean isFocusable() {
    return false;
  }
}
