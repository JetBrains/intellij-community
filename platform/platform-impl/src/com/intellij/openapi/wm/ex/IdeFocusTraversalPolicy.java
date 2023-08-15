// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.lang.reflect.Field;

import static com.intellij.util.ui.FocusUtil.findFocusableComponentIn;

public class IdeFocusTraversalPolicy extends LayoutFocusTraversalPolicy {
  private static final SwingDefaultFocusTraversalPolicy DEFAULT_TRAVERSAL_POLICY = new SwingDefaultFocusTraversalPolicy();

  @Override
  public Component getDefaultComponent(Container focusCycleRoot) {
    if (!(focusCycleRoot instanceof JComponent)) {
      return super.getDefaultComponent(focusCycleRoot);
    }
    return getPreferredFocusedComponent((JComponent)focusCycleRoot, this);
  }

  public static JComponent getPreferredFocusedComponent(@NotNull JComponent component) {
    return getPreferredFocusedComponent(component, null);
  }

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    Component after = super.getComponentAfter(aContainer, aComponent);
    return doFind(aContainer, aComponent, after);
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    Component before = super.getComponentBefore(aContainer, aComponent);
    return doFind(aContainer, aComponent, before);
  }

  protected @Nullable Project getProject() {
    return null;
  }

  private @Nullable Component doFind(Container aContainer, Component aComponent, @Nullable Component siblingComponent) {
    if (siblingComponent == null) {
      return findFocusableComponentIn(aContainer, aComponent);
    }

    if (siblingComponent instanceof EditorsSplitters) {
      Component defaultFocusableComponent = EditorsSplitters.findDefaultComponentInSplitters(getProject());
      if (defaultFocusableComponent != null) {
        return defaultFocusableComponent;
      }
    }

    return siblingComponent.isFocusable() ? siblingComponent : findFocusableComponentIn(siblingComponent, null);
  }

  /**
   * @return preferred focused component inside the specified {@code component}.
   * Method can return component itself if the {@code component} is legal
   * (JTextField)focusable
   */
  public static @Nullable JComponent getPreferredFocusedComponent(@NotNull JComponent component, @Nullable FocusTraversalPolicy policyToIgnore) {
    return getPreferredFocusedComponent(component, policyToIgnore, null);
  }

  private static @Nullable JComponent getPreferredFocusedComponent(@NotNull JComponent component,
                                                                   @Nullable FocusTraversalPolicy policyToIgnore,
                                                                   @Nullable Field focusTraversalPolicyField) {
    if (!component.isVisible()) {
      return null;
    }

    FocusTraversalPolicy focusTraversalPolicy = null;
    if (component.isFocusTraversalPolicySet()) {
      try {
        focusTraversalPolicyField = Container.class.getDeclaredField("focusTraversalPolicy");
        focusTraversalPolicyField.setAccessible(true);
      }
      catch (ReflectiveOperationException e) {
        focusTraversalPolicyField = null;
      }

      try {
        focusTraversalPolicy =
          focusTraversalPolicyField != null && component.isFocusTraversalPolicySet() ? (FocusTraversalPolicy)focusTraversalPolicyField.get(
            component) : null;
      }
      catch (IllegalAccessException ignored) {
      }
    }

    if (focusTraversalPolicy != null && focusTraversalPolicy != policyToIgnore) {
      if (focusTraversalPolicy.getClass().getName().contains("LegacyGlueFocusTraversalPolicy")) {
        return component;
      }

      Component defaultComponent = focusTraversalPolicy.getDefaultComponent(component);
      if (defaultComponent instanceof JComponent) {
        return (JComponent)defaultComponent;
      }
    }

    if (component instanceof JTabbedPane tabbedPane) {
      Component selectedComponent = tabbedPane.getSelectedComponent();
      if (selectedComponent instanceof JComponent) {
        return getPreferredFocusedComponent((JComponent)selectedComponent);
      }
      return null;
    }

    if (_accept(component)) {
      return component;
    }

    for (Component ca : component.getComponents()) {
      if (!(ca instanceof JComponent)) {
        continue;
      }

      JComponent c = getPreferredFocusedComponent((JComponent)ca, null, focusTraversalPolicyField);
      if (c != null) {
        return c;
      }
    }
    return null;
  }

  @Override
  protected final boolean accept(Component aComponent) {
    if (aComponent instanceof JComponent) {
      return _accept((JComponent)aComponent);
    }
    return super.accept(aComponent);
  }

  private static boolean _accept(@NotNull JComponent component) {
    if (!component.isEnabled() || !component.isVisible() || !component.isFocusable()) {
      return false;
    }

    if (component instanceof EditorComponentImpl || component instanceof EditorWindowHolder) {
      return true;
    }

    if (component instanceof JTextComponent) {
      return ((JTextComponent)component).isEditable();
    }

    if (component instanceof JLabel) {
      return DEFAULT_TRAVERSAL_POLICY.accept(component);
    }

    return component instanceof AbstractButton ||
           component instanceof JList ||
           component instanceof JTree ||
           component instanceof JTable ||
           component instanceof JComboBox;
  }

  // Create our own subclass and change accept to public so that we can call accept.
  private static final class SwingDefaultFocusTraversalPolicy extends DefaultFocusTraversalPolicy {
    @Override
    public boolean accept(Component aComponent) {
      return super.accept(aComponent);
    }
  }
}
