// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * @param <K>  the type of an object used as a key
 * @param <UI> the type of an object used to create a component
 * @param <V>  the type of a component to create
 */
public abstract class CardLayoutPanel<K, UI, V extends Component> extends JComponent implements Accessible, Disposable {
  private final IdentityHashMap<K, V> myContent = new IdentityHashMap<>();
  private volatile boolean myDisposed;
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

  protected void dispose(K key, V value) {
  }

  @Override
  public void dispose() {
    if (!myDisposed) {
      myDisposed = true;
      removeAll();
    }
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
      boolean wasFocused = UIUtil.isFocusAncestor(this);
      for (Component component : getComponents()) {
        component.setVisible(component == value);
      }
      if (value instanceof JScrollPane) {
        JScrollPane pane = (JScrollPane)value;
        JViewport viewport = pane.getViewport();
        if (viewport != null) {
          Component view = viewport.getView();
          if (view != null) {
            view.revalidate();
            view.repaint();
          }
        }
      }
      if (wasFocused && value instanceof JComponent) {
        JComponent focusable = IdeFocusManager.getGlobalInstance().getFocusTargetFor((JComponent)value);
        if (focusable != null) focusable.requestFocusInWindow();
      }
      callback.setDone();
    }
  }

  private void selectLater(final ActionCallback callback, final K key) {
    ModalityState modality = ModalityState.stateForComponent(this);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (!myDisposed) {
        final UI ui1 = prepare(key);
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!myDisposed) {
            select(callback, key, ui1);
          }
          else callback.setRejected();
        }, modality);
      }
      else callback.setRejected();
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

  private @Nullable Component getVisibleComponent() {
    for (Component component : getComponents()) {
      if (component.isVisible()) return component;
    }
    return null;
  }

  @Override
  public Dimension getPreferredSize() {
    Component component = isPreferredSizeSet() ? null : getVisibleComponent();
    if (component == null) return super.getPreferredSize();
    // preferred size of a visible component plus border insets of this panel
    Dimension size = component.getPreferredSize();
    JBInsets.addTo(size, getInsets()); // add border of this panel
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    Component component = isMinimumSizeSet() ? null : getVisibleComponent();
    if (component == null) return super.getMinimumSize();
    // minimum size of a visible component plus border insets of this panel
    Dimension size = component.getMinimumSize();
    JBInsets.addTo(size, getInsets());
    return size;
  }

  @Override
  public void remove(int index) {
    super.remove(index);
    // dispose corresponding entries only
    IdentityHashMap<K, V> map = new IdentityHashMap<>();
    Iterator<Entry<K, V>> it = myContent.entrySet().iterator();
    while (it.hasNext()) {
      Entry<K, V> entry = it.next();
      V value = entry.getValue();
      if (value != null && this != value.getParent()) {
        map.put(entry.getKey(), value);
        it.remove();
      }
    }
    // avoid ConcurrentModificationException
    map.forEach(this::dispose);
  }

  @Nullable
  protected final V resetValue(@NotNull K key) {
    V content = myContent.remove(key);
    if (content != null) {
      for (Component component : getComponents()) {
        if (component == content) {
          remove(component);
        }
      }
      if (myKey == key) {
        //select again
        myKey = null;
        select(key, true);
      }
    }

    return content;
  }

  @Override
  public void removeAll() {
    super.removeAll();
    // dispose all entries
    IdentityHashMap<K, V> map = new IdentityHashMap<>(myContent);
    myContent.clear();
    // avoid ConcurrentModificationException
    map.forEach(this::dispose);
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleCardLayoutPanel();
    }
    return accessibleContext;
  }

  protected class AccessibleCardLayoutPanel extends AccessibleJComponent {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PANEL;
    }
  }

  protected final boolean isDisposed() {
    return myDisposed;
  }
}
