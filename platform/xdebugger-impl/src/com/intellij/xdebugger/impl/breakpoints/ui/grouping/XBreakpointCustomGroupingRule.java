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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Egor
 */
public class XBreakpointCustomGroupingRule<B> extends XBreakpointGroupingRule<B, XBreakpointCustomGroup> {
  public XBreakpointCustomGroupingRule() {
    super("by-group", "Show user groups");
  }

  @Override
  public int getPriority() {
    return 1200;
  }

  @Override
  public boolean isAlwaysEnabled() {
    return true;
  }

  public XBreakpointCustomGroup getGroup(@NotNull final B breakpoint, @NotNull final Collection<XBreakpointCustomGroup> groups) {
    if (!(breakpoint instanceof XBreakpointBase)) {
      return null;
    }
    String name = ((XBreakpointBase)breakpoint).getGroup();
    if (StringUtil.isEmpty(name)) {
      return null;
    }
    return new XBreakpointCustomGroup(name, ((XBreakpointBase)breakpoint).getProject());
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.NewFolder;
  }
}
