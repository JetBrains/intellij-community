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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static java.awt.GridBagConstraints.CENTER;

/**
 * @author Denis Fokin
 *
 * JComboBox<String> comboBox = new ComboBox<>(new String[] {"First", "Second", "Third"});
 * comboBox.setEditable(true);
 * comboBox.setEditor(new ComboBoxCompositeEditor(new EditorTextField(), new JLabel(AllIcons.Icon_CE)));
 * @param <I>
 * @param <F>
 */

public class ComboBoxCompositeEditor<I, F extends JComponent> extends JPanel implements ComboBoxEditor {

  public static <ItemType, EditableComponentType extends JComponent> ComboBoxCompositeEditor<ItemType, EditableComponentType> withComponents (final EditableComponentType editableComponent, final JComponent ... components)
  {
    return new ComboBoxCompositeEditor<>(editableComponent, components);
  }

  private BiConsumer<I, F>  myOnSetItemHandler = null;
  private BiFunction<I, F, I>  myOnGetItemHandler = null;

  public ComboBoxCompositeEditor<I, F> onSetItem (BiConsumer<I, F> onSetItemHandler) {
    myOnSetItemHandler = onSetItemHandler;
    return this;
  }

  public ComboBoxCompositeEditor<I, F> onGetItem (BiFunction<I, F, I> onGetItemHandler) {
    myOnGetItemHandler = onGetItemHandler;
    return this;
  }

  public interface EditorComponent<F, I> {
    void setItem(I anObject);
    I getItem();
    void selectAll();
    void addActionListener(ActionListener l);
    void removeActionListener(ActionListener l);
    JComponent getDelegate();
  }

  private ComboBoxCompositeEditorStrategy getStrategy(F focusableComponent) {
    ComboBoxCompositeEditorStrategy strategy = null;
    if (focusableComponent instanceof JTextField) {
      strategy = jTextFieldStrategy;
    } else if (focusableComponent instanceof EditorTextField) {
      strategy = editorTextFieldStrategy;
    }
    return strategy;
  }

  abstract class ComboBoxCompositeEditorStrategy {

    abstract void setItem(F component, I anObject);

    abstract void selectAll(F component);

    abstract void addActionListener(F component, ActionListener l) ;

    abstract void removeActionListener(F component, ActionListener l) ;

    abstract I getItem(F component, I item);
  }

  private final ComboBoxCompositeEditorStrategy editorTextFieldStrategy = new ComboBoxCompositeEditorStrategy () {

    BiConsumer<I, EditorTextField> defaultOnSetHandler = (anObject, component) -> component.setText((anObject == null) ? "" : anObject.toString());

    public void setItem(F component, I anObject) {
      if (myOnSetItemHandler == null) {
        defaultOnSetHandler.accept(anObject, (EditorTextField)component);
      } else {
        myOnSetItemHandler.accept(anObject, component);
      }
    }

    public I getItem(F component, I anObject) {
      if (myOnGetItemHandler == null) {
        return anObject;
      } else {
        return myOnGetItemHandler.apply(anObject, component);
      }
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

  private final ComboBoxCompositeEditorStrategy jTextFieldStrategy =  new ComboBoxCompositeEditorStrategy() {

    BiConsumer<I, JTextField> defaultOnSetHandler = (anObject, component) ->  component.setText((anObject ==null) ? "" : anObject.toString());

    public void setItem(F component, I anObject) {
      if (myOnSetItemHandler == null) {
        defaultOnSetHandler.accept(anObject, (JTextField)component);
      } else {
        myOnSetItemHandler.accept(anObject, component);
      }
    }

    public I getItem(F component, I anObject) {
      if (myOnGetItemHandler == null) {
        return anObject;
      } else {
        return myOnGetItemHandler.apply(anObject, component);
      }
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

  private final EditorComponent<F,I> myComponent;

  public ComboBoxCompositeEditor(final F editableComponent, final JComponent ... components) {
    assert components.length > 0;
    setLayout(new GridBagLayout());
    setFocusable(false);

    GridBagConstraints c = new GridBagConstraints();

    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1;
    c.gridy = 0;
    c.anchor = CENTER;
    c.ipadx = 0;
    c.ipady = 0;
    c.gridx = 0;

    add(editableComponent, c);
    c.weightx = 0;

    for (int i = 0; i < components.length; i ++) {
      c.gridx = i + 1;
      add(components[i], c);
    }

    final ComboBoxCompositeEditorStrategy strategy = getStrategy(editableComponent);

    myComponent = new ComboBoxCompositeEditor.EditorComponent<F, I>() {

      I myItem;

      public void setItem(I anObject) {
        myItem = anObject;
        strategy.setItem(editableComponent, anObject);
      }

      public I getItem() {
        return strategy.getItem(editableComponent, myItem);
      }

      @Override
      public void selectAll() {
        strategy.selectAll(editableComponent);
      }

      @Override
      public void addActionListener(ActionListener l) {
        strategy.addActionListener(editableComponent, l);
      }

      @Override
      public void removeActionListener(ActionListener l) {
        strategy.removeActionListener(editableComponent, l);
      }

      @Override
      public JComponent getDelegate() {
        return editableComponent;
      }
    };

    invalidate();

    editableComponent.setBorder(null);
    editableComponent.addFocusListener(new FocusListener() {

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
    myComponent.setItem((I)anObject);
  }

  @Override
  public Object getItem() {
    return myComponent.getItem();
  }

  @Override
  public void selectAll() {
    myComponent.selectAll();
  }

  @Override
  public void addActionListener(ActionListener l) {
    myComponent.addActionListener(l);
  }

  @Override
  public void removeActionListener(ActionListener l) {
    myComponent.removeActionListener(l);
  }

  @Override
  public void setFocusable(boolean focusable) {}

  @Override
  public boolean isFocusable() {
    return false;
  }

  @Override
  public Color getBackground() {
    return myComponent == null ? super.getBackground() : myComponent.getDelegate().getBackground();
  }

  @Override
  public void setBackground(Color color) {
    if (myComponent == null) {
      super.setBackground(color);
    } else {
      myComponent.getDelegate().setBackground(color);
    }
  }
}
