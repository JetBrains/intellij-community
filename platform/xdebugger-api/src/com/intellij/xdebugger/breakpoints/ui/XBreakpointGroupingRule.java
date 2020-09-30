// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.breakpoints.ui;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;

public abstract class XBreakpointGroupingRule<B, G extends XBreakpointGroup> {
  public static final Comparator<XBreakpointGroupingRule> PRIORITY_COMPARATOR = (o1, o2) -> {
    final int res = o2.getPriority() - o1.getPriority();
    return res != 0 ? res : (o1.getId().compareTo(o2.getId()));
  };

  private final String myId;
  private final @Nls String myPresentableName;

  public boolean isAlwaysEnabled() {
    return false;
  }

  protected XBreakpointGroupingRule(final @NotNull @NonNls String id, final @Nls String presentableName) {
    myId = id;
    myPresentableName = presentableName;
  }

  @NotNull
  @Nls
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
  public abstract G getGroup(@NotNull B breakpoint, @NotNull Collection<? extends G> groups);

  @Nullable
  public Icon getIcon() {
    return null;
  }
}
