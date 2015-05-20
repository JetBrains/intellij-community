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
import com.intellij.util.ui.JBInsets;

import javax.swing.JComponent;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * @param <K>  the type of an object used as a key
 * @param <UI> the type of an object used to create a component
 * @param <V>  the type of a component to create
 *
 * @author Sergey.Malenkov
 */
public abstract class CardLayoutPanel<K, UI, V extends Component> extends JComponent {
  private final IdentityHashMap<K, V> myContent = new IdentityHashMap<K, V>();
  private K myKey;

  /**
   * Prepares the specified key object to return the temporary object
   * that can be used to create a component view.
   * This method may be called on a pooled thread.
   *
   * @param key the key object
   * @return the object used to create a component
   */
  protected abstract UI prepare(K key);

  /**
   * Creates a component view from the temporary object.
   * This method is usually called on the EDT.
   *
   * @param ui
   * @return
   */
  protected abstract V create(UI ui);

  protected void dispose(K key) {
  }

  public K getKey() {
    return myKey;
  }

  public V getValue(K key, boolean create) {
    V value = myContent.get(key);
    return create && value == null && !myContent.containsKey(key)
           ? createValue(key, prepare(key))
           : value;
  }

  private V createValue(K key, UI ui) {
    V value = create(ui);
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
    if (now) {
      select(callback, key, prepare(key));
    }
    else {
      selectLater(callback, key);
    }
    return callback;
  }

  private void select(ActionCallback callback, K key, UI ui) {
    if (myKey != key) {
      callback.setRejected();
    }
    else {
      V value = myContent.get(key);
      if (value == null && !myContent.containsKey(key)) {
        value = createValue(key, ui);
      }
      for (Component component : getComponents()) {
        component.setVisible(component == value);
      }
      callback.setDone();
    }
  }

  private void selectLater(final ActionCallback callback, final K key) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final UI ui = prepare(key);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            select(callback, key, ui);
          }
        }, ModalityState.any());
      }
    });
  }

  @Override
  public void doLayout() {
    Rectangle bounds = new Rectangle(getWidth(), getHeight());
    JBInsets.removeFrom(bounds, getInsets());
    for (Component component : getComponents()) {
      component.setBounds(bounds);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    for (Component component : getComponents()) {
      if (component.isVisible()) {
        return component.getPreferredSize();
      }
    }
    return super.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }
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
