// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.reference.SoftReference;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.SwingUndoUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.lang.ref.WeakReference;

/**
 * Spies how focus goes in the component.
 */
public class FocusWatcher implements ContainerListener, FocusListener {
  private WeakReference<Component> myTopComponent;
  /**
   * Last component that had focus.
   */
  private WeakReference<Component> myFocusedComponent;
  /**
   * TODO[vova,anton] the name getMostRecentFocusOwner is better. The description could be copied from
   * java.awt.Window.getMostRecentFocusOwner() method.
   * This is the nearest component to the myFocusableComponent
   */
  private WeakReference<Component> myNearestFocusableComponent;

  /**
   * @return top component on which focus watcher was installed.
   * The method always return {@code null} if focus watcher was installed
   * on some component hierarchy.
   */
  public Component getTopComponent() {
    return SoftReference.dereference(myTopComponent);
  }

  @Override
  public final void componentAdded(final ContainerEvent e) {
    installImpl(e.getChild());
  }

  @Override
  public final void componentRemoved(final ContainerEvent e) {
    Component removedChild = e.getChild();
    if (getNearestFocusableComponent() != null && SwingUtilities.isDescendingFrom(getNearestFocusableComponent(), removedChild)) {
      setNearestFocusableComponent(null);
    }
    if (getFocusedComponent() != null && SwingUtilities.isDescendingFrom(getFocusedComponent(), removedChild)) {
      setNearestFocusableComponent(e.getContainer());
    }
    deinstall(removedChild, e);
  }

  public final void deinstall(final Component component) {
    deinstall(component, null);
  }

  public final void deinstall(final Component component, @Nullable AWTEvent cause) {
    if (component == null) {
      return;
    }

    if (component instanceof Container) {
      Container container = (Container)component;
      int componentCount = container.getComponentCount();
      for (int i = 0; i < componentCount; i++) {
        deinstall(container.getComponent(i));
      }
      container.removeContainerListener(this);
    }

    component.removeFocusListener(this);
    if (getFocusedComponent() == component) {
      setFocusedComponentImpl(null, cause);
    }
  }

  @Override
  public final void focusGained(final FocusEvent e) {
    final Component component = e.getComponent();
    if (e.isTemporary() || !component.isShowing()) {
      return;
    }
    if (component instanceof JTextComponent) {
      SwingUndoUtil.addUndoRedoActions((JTextComponent)component);
    }
    setFocusedComponentImpl(component, e);
    setNearestFocusableComponent(component.getParent());
  }

  @Override
  public final void focusLost(final FocusEvent e) {
    Component component = e.getOppositeComponent();
    if (component != null && !SwingUtilities.isDescendingFrom(component, SoftReference.dereference(myTopComponent))) {
      focusLostImpl(e);
    }
  }

  /**
   * @return last focused component or {@code null}.
   */
  public final Component getFocusedComponent() {
    return SoftReference.dereference(myFocusedComponent);
  }

  private Component getNearestFocusableComponent() {
    return SoftReference.dereference(myNearestFocusableComponent);
  }

  public final void install(@NotNull Component component) {
    myTopComponent = new WeakReference<>(component);
    installImpl(component);
  }

  private void installImpl(Component component) {
    if (component instanceof Container) {
      Container container = (Container)component;
      synchronized (container.getTreeLock()) {
        int componentCount = container.getComponentCount();
        for (int i = 0; i < componentCount; i++) {
          installImpl(container.getComponent(i));
        }
        container.addContainerListener(this);
      }
    }

    if (component instanceof JMenuItem || component instanceof JMenuBar) {
      return;
    }
    component.addFocusListener(this);
  }

  public void setFocusedComponentImpl(Component component) {
    setFocusedComponentImpl(component, null);
  }

  private void setFocusedComponentImpl(Component component, @Nullable AWTEvent cause) {
    if (!isFocusedComponentChangeValid(component, cause)) {
      return;
    }

    if (ComponentUtil.isFocusProxy(component)) {
      _setFocused(getFocusedComponent(), cause);
      return;
    }

    _setFocused(component, cause);
  }

  private void _setFocused(final Component component, final AWTEvent cause) {
    setFocusedComponent(component);
    focusedComponentChanged(component, cause);
  }

  protected boolean isFocusedComponentChangeValid(@Nullable Component component, @Nullable AWTEvent cause) {
    return component != null || cause != null;
  }

  /**
   * Override this method to get notifications about focus. {@code FocusWatcher} invokes
   * this method each time one of the populated  component gains focus. All "temporary" focus
   * event are ignored.
   *
   * @param component currently focused component. The component can be {@code null}
   */
  protected void focusedComponentChanged(@Nullable Component component, @Nullable AWTEvent cause) {}

  protected void focusLostImpl(FocusEvent e) {
  }

  private void setFocusedComponent(final Component focusedComponent) {
    myFocusedComponent = new WeakReference<>(focusedComponent);
  }

  private void setNearestFocusableComponent(final Component nearestFocusableComponent) {
    myNearestFocusableComponent = new WeakReference<>(nearestFocusableComponent);
  }
}