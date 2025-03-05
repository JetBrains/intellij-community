// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Represents a group of values in a debugger tree.
 */
public abstract class XValueGroup extends XValueContainer {
  private final String myName;

  protected XValueGroup(@NotNull String name) {
    myName = name;
  }

  public @NotNull @NlsSafe String getName() {
    return myName;
  }

  public @Nullable Icon getIcon() {
    return null;
  }

  /**
   * @return {@code true} to automatically expand the group node when it's added to a tree
   */
  public boolean isAutoExpand() {
    return false;
  }

  /**
   * @return {@code true} to save and restore expansion state between sessions.
   * If it is enabled, return value from {@code isAutoExpand()} will be considered as default state.
   */
  public boolean  isRestoreExpansion() {
    return false;
  }

  /**
   * @return separator between the group name and the {@link #getComment() comment} in the node text
   */
  public @NotNull @NlsSafe String getSeparator() {
    return " = ";
  }

  /**
   * @return optional comment shown after the group name
   */
  public @Nullable @NlsSafe String getComment() {
    return null;
  }
}