// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy;
import com.intellij.xdebugger.impl.rpc.XBreakpointId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public abstract class BreakpointItem extends ItemWrapper implements Comparable<BreakpointItem>, Navigatable {
  public static final Key<Object> EDITOR_ONLY = Key.create("EditorOnly");

  public abstract void saveState();

  public abstract @Nullable XBreakpointProxy getBreakpoint();

  public abstract boolean isEnabled();

  public abstract void setEnabled(boolean state);

  public abstract boolean isDefaultBreakpoint();

  @ApiStatus.Internal
  public abstract @Nullable XBreakpointId getId();

  @Override
  public void updateAccessoryView(JComponent component) {
    final JCheckBox checkBox = (JCheckBox)component;
    checkBox.setSelected(isEnabled());
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    setupGenericRenderer(renderer, true);
  }

  @Override
  public void setupRenderer(ColoredTreeCellRenderer renderer, Project project, boolean selected) {
    boolean plainView = renderer.getTree().getClientProperty("plainView") != null;
    setupGenericRenderer(renderer, plainView);
  }


  public abstract void setupGenericRenderer(SimpleColoredComponent renderer, boolean plainView);

  public abstract Icon getIcon();

  public abstract @Nls String getDisplayText();

  protected void dispose() { }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BreakpointItem item = (BreakpointItem)o;

    if (getBreakpoint() != null ? !getBreakpoint().equals(item.getBreakpoint()) : item.getBreakpoint() != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return getBreakpoint() != null ? getBreakpoint().hashCode() : 0;
  }
}
