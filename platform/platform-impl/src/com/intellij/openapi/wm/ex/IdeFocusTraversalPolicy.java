// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class IdeFocusTraversalPolicy extends LayoutFocusTraversalPolicyExt {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy");

  @Override
  protected Component getDefaultComponentImpl(Container focusCycleRoot) {
    if (!(focusCycleRoot instanceof JComponent)) {
      return super.getDefaultComponent(focusCycleRoot);
    }
    return getPreferredFocusedComponent((JComponent)focusCycleRoot, this);
  }

  public static JComponent getPreferredFocusedComponent(@NotNull final JComponent component) {
    return getPreferredFocusedComponent(component, null);
  }

  /**
   * @return preferred focused component inside the specified {@code component}.
   * Method can return component itself if the {@code component} is legal
   * (JTextFiel)focusable
   *
   */
  public static JComponent getPreferredFocusedComponent(@NotNull final JComponent component, final FocusTraversalPolicy policyToIgnore) {
    if (!component.isVisible()) {
      return null;
    }

    final FocusTraversalPolicy focusTraversalPolicy = getFocusTraversalPolicyAwtImpl(component);
    if (focusTraversalPolicy != null && focusTraversalPolicy != policyToIgnore) {
      if (focusTraversalPolicy.getClass().getName().indexOf("LegacyGlueFocusTraversalPolicy") >=0) {
        return component;
      }

      Component defaultComponent;
      if (focusTraversalPolicy instanceof LayoutFocusTraversalPolicyExt) {
        final LayoutFocusTraversalPolicyExt extPolicy = (LayoutFocusTraversalPolicyExt)focusTraversalPolicy;
        defaultComponent = extPolicy.queryImpl(() -> extPolicy.getDefaultComponent(component));
      } else {
        defaultComponent = focusTraversalPolicy.getDefaultComponent(component);
      }

      if (defaultComponent instanceof JComponent) {
        return (JComponent)defaultComponent;
      }
    }

    if (component instanceof JTabbedPane) {
      final JTabbedPane tabbedPane = (JTabbedPane)component;
      final Component selectedComponent = tabbedPane.getSelectedComponent();
      if (selectedComponent instanceof JComponent) {
        return getPreferredFocusedComponent((JComponent)selectedComponent);
      }
      return null;
    }

    if(_accept(component)) {
      return component;
    }

    for (Component ca : component.getComponents()) {
      if (!(ca instanceof JComponent)) {
        continue;
      }
      final JComponent c = getPreferredFocusedComponent((JComponent)ca);
      if (c != null) {
        return c;
      }
    }
    return null;
  }

  private static FocusTraversalPolicy getFocusTraversalPolicyAwtImpl(final JComponent component) {
    return ReflectionUtil.getField(Container.class, component, FocusTraversalPolicy.class, "focusTraversalPolicy");
  }

  @Override
  protected final boolean accept(final Component aComponent) {
    if (aComponent instanceof JComponent) {
      return _accept((JComponent)aComponent);
    }
    return super.accept(aComponent);
  }

  private static boolean _accept(final JComponent component) {
    if (!component.isEnabled() || !component.isVisible() || !component.isFocusable()) {
      return false;
    }

    /* TODO[anton,vova] implement Policy in Editor component instead */
    if (component instanceof EditorComponentImpl || component instanceof EditorWindowHolder) {
      return true;
    }

    if(component instanceof JTextComponent){
      return ((JTextComponent)component).isEditable();
    }

    return
      component instanceof AbstractButton ||
      component instanceof JList ||
      component instanceof JTree ||
      component instanceof JTable ||
      component instanceof JComboBox;
  }
}
