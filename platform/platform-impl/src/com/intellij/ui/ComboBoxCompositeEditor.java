// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  private BiConsumer<? super I, ? super F> myOnSetItemHandler = null;
  private BiFunction<? super I, ? super F, ? extends I> myOnGetItemHandler = null;

  public ComboBoxCompositeEditor<I, F> onSetItem (BiConsumer<? super I, ? super F> onSetItemHandler) {
    myOnSetItemHandler = onSetItemHandler;
    return this;
  }

  public ComboBoxCompositeEditor<I, F> onGetItem (BiFunction<? super I, ? super F, ? extends I> onGetItemHandler) {
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
    }
    else if (focusableComponent instanceof EditorTextField) {
      strategy = editorTextFieldStrategy;
    }
    else if (focusableComponent instanceof JLabel) {
      strategy = jLabelStrategy;
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

    final BiConsumer<I, EditorTextField> defaultOnSetHandler = (anObject, component) -> component.setText((anObject == null) ? "" : anObject.toString());

    @Override
    public void setItem(F component, I anObject) {
      if (myOnSetItemHandler == null) {
        defaultOnSetHandler.accept(anObject, (EditorTextField)component);
      } else {
        myOnSetItemHandler.accept(anObject, component);
      }
    }

    @Override
    public I getItem(F component, I anObject) {
      if (myOnGetItemHandler == null) {
        return anObject;
      } else {
        return myOnGetItemHandler.apply(anObject, component);
      }
    }

    @Override
    public void selectAll(JComponent component) {
      ((EditorTextField)component).selectAll();
    }

    @Override
    public void addActionListener(JComponent component, ActionListener l) {
      //((EditorTextField)component).addActionListener(l);
    }

    @Override
    public void removeActionListener(JComponent component, ActionListener l) {
      //((EditorTextField)component).removeActionListener(l);
    }
  };

  private final ComboBoxCompositeEditorStrategy jTextFieldStrategy =  new ComboBoxCompositeEditorStrategy() {

    final BiConsumer<I, JTextField> defaultOnSetHandler = (anObject, component) ->  component.setText((anObject == null) ? "" : anObject.toString());

    @Override
    public void setItem(F component, I anObject) {
      if (myOnSetItemHandler == null) {
        defaultOnSetHandler.accept(anObject, (JTextField)component);
      } else {
        myOnSetItemHandler.accept(anObject, component);
      }
    }

    @Override
    public I getItem(F component, I anObject) {
      if (myOnGetItemHandler == null) {
        return anObject;
      } else {
        return myOnGetItemHandler.apply(anObject, component);
      }
    }

    @Override
    public void selectAll(JComponent component) {
      ((JTextField)component).selectAll();
    }

    @Override
    public void addActionListener(JComponent component, ActionListener l) {
      ((JTextField)component).addActionListener(l);
    }

    @Override
    public void removeActionListener(JComponent component, ActionListener l) {
      ((JTextField)component).removeActionListener(l);
    }
  };

  private final ComboBoxCompositeEditorStrategy jLabelStrategy =  new ComboBoxCompositeEditorStrategy() {
    final BiConsumer<I, JLabel> defaultOnSetHandler = (anObject, component) ->  component.setText((anObject == null) ? "" : anObject.toString());

    @Override
    public void setItem(F component, I anObject) {
      if (myOnSetItemHandler == null) {
        defaultOnSetHandler.accept(anObject, (JLabel)component);
      } else {
        myOnSetItemHandler.accept(anObject, component);
      }
    }

    @Override
    public I getItem(F component, I anObject) {
      if (myOnGetItemHandler == null) {
        return anObject;
      } else {
        return myOnGetItemHandler.apply(anObject, component);
      }
    }

    @Override
    public void selectAll(JComponent component) {}

    @Override
    public void addActionListener(JComponent component, ActionListener l) {}

    @Override
    public void removeActionListener(JComponent component, ActionListener l) {}
  };

  private final ComboBoxCompositeEditorStrategy myStrategy;
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

    myStrategy = getStrategy(editableComponent);

    myComponent = new ComboBoxCompositeEditor.EditorComponent<>() {

      I myItem;

      @Override
      public void setItem(I anObject) {
        myItem = anObject;
        myStrategy.setItem(editableComponent, anObject);
      }

      @Override
      public I getItem() {
        return myStrategy.getItem(editableComponent, myItem);
      }

      @Override
      public void selectAll() {
        myStrategy.selectAll(editableComponent);
      }

      @Override
      public void addActionListener(ActionListener l) {
        myStrategy.addActionListener(editableComponent, l);
      }

      @Override
      public void removeActionListener(ActionListener l) {
        myStrategy.removeActionListener(editableComponent, l);
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

  public boolean isEditable() {
    return myStrategy != jLabelStrategy;
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
