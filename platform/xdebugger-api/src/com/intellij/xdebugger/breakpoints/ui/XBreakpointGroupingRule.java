/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.breakpoints.ui;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;

/**
 * @author nik
 */
public abstract class XBreakpointGroupingRule<B, G extends XBreakpointGroup> {
  public static final Comparator<XBreakpointGroupingRule> PRIORITY_COMPARATOR = new Comparator<XBreakpointGroupingRule>() {
    @Override
    public int compare(XBreakpointGroupingRule o1, XBreakpointGroupingRule o2) {
      final int res = o2.getPriority() - o1.getPriority();
      return res != 0 ? res : (o1.getId().compareTo(o2.getId()));
    }
  };

  private final String myId;
  private final String myPresentableName;

  public boolean isAlwaysEnabled() {
    return false;
  }

  protected XBreakpointGroupingRule(final @NotNull @NonNls String id, final @NonNls @Nls String presentableName) {
    myId = id;
    myPresentableName = presentableName;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull 
  public String getId() {
    return myId;
  }

  public int getPriority() {
    return XBreakpointsGroupingPriorities.DEFAULT;
  }

  @Nullable
  public abstract G getGroup(@NotNull B breakpoint, @NotNull Collection<G> groups);

  @Nullable
  public Icon getIcon() {
    return null;
  }
}
