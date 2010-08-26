package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Gregory.Shrago
 */
public abstract class SwingActionWrapper<T extends JComponent> implements Action {
  private Action myDelegate;
  private T myComponent;
  private Object myActionKey;

  public SwingActionWrapper(T component, KeyStroke keyStroke) {
    myComponent = component;
    Object actionKey = getKeyForActionMap(component, keyStroke);
    if (actionKey == null) {
      String message = "no input mapping for keyStroke: " + keyStroke;
      throw new IllegalArgumentException(message);
    }
    setActionForKey(actionKey);
  }

  public Action getDelegate() {
    return myDelegate;
  }

  public T getComponent() {
    return myComponent;
  }

  public Object getActionKey() {
    return myActionKey;
  }

  public SwingActionWrapper(T component, Object actionKey) {
    myComponent = component;
    setActionForKey(actionKey);
  }

  @Nullable
  private static Object getKeyForActionMap(JComponent component, KeyStroke keyStroke) {
    for (int i = 0; i < 3; i++) {
      final InputMap inputMap = component.getInputMap(i);
      if (inputMap == null) continue;
      final Object key = inputMap.get(keyStroke);
      if (key != null) {
        return key;
      }
    }
    return null;
  }

  private void setActionForKey(Object actionKey) {
    myActionKey = actionKey;
    myDelegate = myComponent.getActionMap().get(actionKey);
    assert myDelegate != null: "action not found: " + actionKey;
    myComponent.getActionMap().put(myActionKey, this);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myDelegate.addPropertyChangeListener(listener);
  }

  public Object getValue(String key) {
    return myDelegate.getValue(key);
  }

  public boolean isEnabled() {
    return myDelegate.isEnabled();
  }

  public void putValue(String key, Object newValue) {
    myDelegate.putValue(key, newValue);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myDelegate.removePropertyChangeListener(listener);
  }

  public void setEnabled(boolean newValue) {
    myDelegate.setEnabled(newValue);
  }
}