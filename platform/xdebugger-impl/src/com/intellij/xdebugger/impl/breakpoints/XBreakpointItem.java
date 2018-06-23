// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.XLightBreakpointPropertiesPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class XBreakpointItem extends BreakpointItem {
  private final XBreakpoint<?> myBreakpoint;
  private XLightBreakpointPropertiesPanel myPropertiesPanel;

  public XBreakpointItem(XBreakpoint<?> breakpoint) {
    myBreakpoint = breakpoint;
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    setupGenericRenderer(renderer, true);
  }

  @Override
  public void setupRenderer(ColoredTreeCellRenderer renderer, Project project, boolean selected) {
    setupGenericRenderer(renderer, false);
  }

  @Override
  public void setupGenericRenderer(SimpleColoredComponent renderer, boolean plainView) {
    if (plainView) {
      renderer.setIcon(getIcon());
    }
    final SimpleTextAttributes attributes =
      myBreakpoint.isEnabled() ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
    renderer.append(StringUtil.notNullize(getDisplayText()), attributes);
    String description = getUserDescription();
    if (!StringUtil.isEmpty(description)) {
      renderer.append(" (" + description + ")", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
    }
  }

  @Override
  public String getDisplayText() {
    return XBreakpointUtil.getShortText(myBreakpoint);
  }

  @Nullable
  private String getUserDescription() {
    return ((XBreakpointBase)myBreakpoint).getUserDescription();
  }

  @Override
  public Icon getIcon() {
    return ((XBreakpointBase)myBreakpoint).getIcon();
  }

  @Override
  public String speedSearchText() {
    return getDisplayText() + " " + StringUtil.notNullize(getUserDescription());
  }

  @Override
  public String footerText() {
    return XBreakpointUtil.getDisplayText(myBreakpoint);
  }

  @Override
  public void saveState() {
    if (myPropertiesPanel != null) {
      myPropertiesPanel.saveProperties();
    }
  }

  @Override
  public void doUpdateDetailView(DetailView panel, boolean editorOnly) {
    XBreakpointBase breakpoint = (XBreakpointBase)myBreakpoint;
    Project project = breakpoint.getProject();
    //saveState();
    if (myPropertiesPanel != null) {
      myPropertiesPanel.dispose();
      myPropertiesPanel = null;
    }
    if (!editorOnly) {
      myPropertiesPanel = new XLightBreakpointPropertiesPanel(project, getManager(), breakpoint, true);

      panel.setPropertiesPanel(myPropertiesPanel.getMainPanel());
    }

    XSourcePosition sourcePosition = myBreakpoint.getSourcePosition();
    if (sourcePosition != null && sourcePosition.getFile().isValid()) {
      showInEditor(panel, sourcePosition.getFile(), sourcePosition.getLine());
    }
    else {
      panel.clearEditor();
    }

    if (myPropertiesPanel != null) {
      myPropertiesPanel.setDetailView(panel);
      myPropertiesPanel.loadProperties();
      myPropertiesPanel.getMainPanel().revalidate();

    }

  }

  @Override
  public void navigate(boolean requestFocus) {
    Navigatable navigatable = myBreakpoint.getNavigatable();
    if (navigatable != null && navigatable.canNavigate()) {
      navigatable.navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    Navigatable navigatable = myBreakpoint.getNavigatable();
    return navigatable != null && navigatable.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    Navigatable navigatable = myBreakpoint.getNavigatable();
    return navigatable != null && navigatable.canNavigateToSource();
  }

  private XBreakpointManagerImpl getManager() {
    return ((XBreakpointBase)myBreakpoint).getBreakpointManager();
  }

  @Override
  public boolean allowedToRemove() {
    return !getManager().isDefaultBreakpoint(myBreakpoint);
  }

  @Override
  public void removed(Project project) {
    XDebuggerUtil.getInstance().removeBreakpoint(project, myBreakpoint);
  }

  @Override
  public Object getBreakpoint() {
    return myBreakpoint;
  }

  @Override
  public boolean isEnabled() {
    return myBreakpoint.isEnabled();
  }

  @Override
  public void setEnabled(boolean state) {
    myBreakpoint.setEnabled(state);
  }

  @Override
  public boolean isDefaultBreakpoint() {
    return getManager().isDefaultBreakpoint(myBreakpoint);
  }

  @Override
  public int compareTo(BreakpointItem breakpointItem) {
    if (breakpointItem.getBreakpoint() instanceof XBreakpointBase) {
      return ((XBreakpointBase)myBreakpoint).compareTo((XBreakpoint)breakpointItem.getBreakpoint());
    }
    else {
      return 0;
    }
  }

  @Override
  public void dispose() {
    if (myPropertiesPanel != null) {
      myPropertiesPanel.dispose();
      myPropertiesPanel = null;
    }
  }
}
