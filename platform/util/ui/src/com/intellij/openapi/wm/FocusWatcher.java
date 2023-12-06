// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.reference.SoftReference;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.SwingUndoUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.lang.ref.WeakReference;

/**
 * Spies how focus goes in the component.
 */
public class FocusWatcher extends BaseFocusWatcher {
  /**
   * Last component that had focus.
   */
  private WeakReference<Component> myFocusedComponent;

  @Override
  protected void componentUnregistered(Component component, @Nullable AWTEvent cause) {
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
    if (component instanceof JTextComponent && ((JTextComponent)component).isEditable()) {
      SwingUndoUtil.addUndoRedoActions((JTextComponent)component);
    }
    setFocusedComponentImpl(component, e);
  }

  @Override
  public final void focusLost(final FocusEvent e) {
    Component component = e.getOppositeComponent();
    if (component != null && !SwingUtilities.isDescendingFrom(component, getTopComponent())) {
      focusLostImpl(e);
    }
  }

  /**
   * @return last focused component or {@code null}.
   */
  public final Component getFocusedComponent() {
    return SoftReference.dereference(myFocusedComponent);
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
    myFocusedComponent = new WeakReference<>(component);
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
}