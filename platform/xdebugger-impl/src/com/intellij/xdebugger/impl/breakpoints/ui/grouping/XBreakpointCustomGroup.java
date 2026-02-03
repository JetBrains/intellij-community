// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * @author Egor
 */
public class XBreakpointCustomGroup extends XBreakpointGroup {
  private final String myName;
  private final boolean myIsDefault;

  public XBreakpointCustomGroup(@NotNull String name, Project project) {
    myName = name;
    myIsDefault = name.equals(XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).getDefaultGroup());
  }

  @Override
  public @Nullable Icon getIcon(final boolean isOpen) {
    return AllIcons.Nodes.Folder;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  public boolean isDefault() {
    return myIsDefault;
  }
}
