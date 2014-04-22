/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @Nullable
  public Icon getIcon(final boolean isOpen) {
    return AllIcons.Nodes.NewFolder;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isDefault() {
    return myIsDefault;
  }
}
