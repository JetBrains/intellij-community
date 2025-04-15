// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;

public abstract class XBreakpointGroupingRule<B, G extends XBreakpointGroup> {
  public static final ExtensionPointName<XBreakpointGroupingRule> EP =
    ExtensionPointName.create("com.intellij.xdebugger.breakpointGroupingRule");

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

  public @NotNull @Nls String getPresentableName() {
    return myPresentableName;
  }

  public @NotNull String getId() {
    return myId;
  }

  public int getPriority() {
    return XBreakpointsGroupingPriorities.DEFAULT;
  }

  public abstract @Nullable G getGroup(@NotNull B breakpoint);

  public @Nullable Icon getIcon() {
    return null;
  }
}
