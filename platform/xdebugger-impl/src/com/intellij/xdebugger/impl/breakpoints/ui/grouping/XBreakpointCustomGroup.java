// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Egor
 */
public class XBreakpointCustomGroup extends XBreakpointGroup {
  private final String myName;
  private final boolean myIsDefault;

  public XBreakpointCustomGroup(@NotNull String name, Project project) {
    myName = name;
    myIsDefault = name.equals(((XBreakpointManagerImpl)XDebuggerManager.getInstance(project).getBreakpointManager()).getDefaultGroup());
  }

  @Override
  @Nullable
  public Icon getIcon(final boolean isOpen) {
    return AllIcons.Nodes.Folder;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isDefault() {
    return myIsDefault;
  }
}
