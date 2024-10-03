// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;


import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

@ApiStatus.Internal
public class BreakpointNoneItem extends BreakpointItem {
  @Override
  public void saveState() {

  }

  @Override
  public Object getBreakpoint() {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void setEnabled(boolean state) {
  }

  @Override
  public boolean isDefaultBreakpoint() {
    return true;
  }

  @Override
  public void setupGenericRenderer(SimpleColoredComponent renderer, boolean plainView) {
    renderer.clear();
    renderer.append(getDisplayText());
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getDisplayText() {
    return XDebuggerBundle.message("xbreakpoint.master.breakpoint.none");
  }

  @Override
  public String speedSearchText() {
    return null;
  }

  @Override
  @Nls
  public String footerText() {
    return "";
  }

  @Override
  protected void doUpdateDetailView(DetailView panel, boolean editorOnly) {
  }

  @Override
  public boolean allowedToRemove() {
    return false;
  }

  @Override
  public void removed(Project project) {
  }

  @Override
  public int compareTo(BreakpointItem breakpointItem) {
    return 1;
  }
}
