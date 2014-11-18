/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;

import javax.swing.JComponent;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * @author Sergey.Malenkov
 */
public abstract class CardLayoutPanel<K, V extends Component> extends JComponent {
  private final IdentityHashMap<K, V> myContent = new IdentityHashMap<K, V>();
  private K myKey;

  protected abstract V create(K key);

  protected void dispose(K key) {
  }

  public K getKey() {
    return myKey;
  }

  public V getValue(K key, boolean create) {
    V value = myContent.get(key);
    return create && value == null && !myContent.containsKey(key)
           ? createValue(key)
           : value;
  }

  private V createValue(K key) {
    V value = create(key);
    myContent.put(key, value);
    if (value != null) {
      value.setVisible(false);
      add(value);
    }
    return value;
  }

  public ActionCallback select(K key, boolean now) {
    myKey = key;
    ActionCallback callback = new ActionCallback();
    select(callback, key, now);
    return callback;
  }

  private void select(ActionCallback callback, K key, boolean now) {
    if (myKey != key) {
      callback.setRejected();
    }
    else {
      V value = myContent.get(key);
      boolean create = value == null && !myContent.containsKey(key);
      if (create && !now) {
        selectLater(callback, key, true);
      }
      else {
        if (create) {
          value = createValue(key);
        }
        for (Component component : getComponents()) {
          component.setVisible(component == value);
        }
        callback.setDone();
      }
    }
  }

  private void selectLater(final ActionCallback callback, final K key, final boolean create) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        select(callback, key, create);
      }
    }, ModalityState.any());
  }

  @Override
  public void doLayout() {
    Rectangle bounds = new Rectangle(getWidth(), getHeight());
    Insets insets = getInsets();
    if (insets != null) {
      bounds.x += insets.left;
      bounds.y += insets.top;
      bounds.width -= insets.left + insets.right;
      bounds.height -= insets.top + insets.bottom;
    }
    for (Component component : getComponents()) {
      component.setBounds(bounds);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    for (Component component : getComponents()) {
      if (component.isVisible()) {
        return component.getPreferredSize();
      }
    }
    return super.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    for (Component component : getComponents()) {
      if (component.isVisible()) {
        return component.getMinimumSize();
      }
    }
    return super.getMinimumSize();
  }

  @Override
  public void remove(int index) {
    super.remove(index);
    // dispose corresponding entries only
    Iterator<Entry<K, V>> it = myContent.entrySet().iterator();
    while (it.hasNext()) {
      Entry<K, V> entry = it.next();
      V value = entry.getValue();
      if (value != null && this != value.getParent()) {
        dispose(entry.getKey());
        it.remove();
      }
    }
  }

  @Override
  public void removeAll() {
    super.removeAll();
    // dispose all entries
    for (K key : myContent.keySet()) {
      dispose(key);
    }
    myContent.clear();
  }
}
