// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class IdeFocusTraversalPolicy extends LayoutFocusTraversalPolicy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy");

  @Override
  public Component getDefaultComponent(Container focusCycleRoot) {
    if (!(focusCycleRoot instanceof JComponent)) {
      return super.getDefaultComponent(focusCycleRoot);
    }
    return getPreferredFocusedComponent((JComponent)focusCycleRoot, this);
  }

  public static JComponent getPreferredFocusedComponent(@NotNull final JComponent component) {
    return getPreferredFocusedComponent(component, null);
  }

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    Component after = super.getComponentAfter(aContainer, aComponent);
    if (!after.isFocusable()) {
      List<Component> components = UIUtil.uiTraverser(after).toList();
      // todo substitute with a stream
      for (int i = 0; i < components.size(); i++) {
        Component component = components.get(i);
        // not sure if the collection includes after
        if (component.equals(after)) {
          continue;
        }
        if (component.isFocusable()) {
          return component;
        }
      }
    }
    return after;
  }

  /**
   * @return preferred focused component inside the specified {@code component}.
   * Method can return component itself if the {@code component} is legal
   * (JTextField)focusable
   *
   */
  public static JComponent getPreferredFocusedComponent(@NotNull final JComponent component, final FocusTraversalPolicy policyToIgnore) {
    if (!component.isVisible()) {
      return null;
    }

    final FocusTraversalPolicy focusTraversalPolicy = getFocusTraversalPolicyAwtImpl(component);
    if (focusTraversalPolicy != null && focusTraversalPolicy != policyToIgnore) {
      if (focusTraversalPolicy.getClass().getName().contains("LegacyGlueFocusTraversalPolicy")) {
        return component;
      }

      Component defaultComponent = focusTraversalPolicy.getDefaultComponent(component);

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
