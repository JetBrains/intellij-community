// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.pom.Navigatable;
import com.intellij.ui.tree.DoubleClickExpandable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface UsageGroup extends Comparable<UsageGroup>, Navigatable, DoubleClickExpandable {
  @Nullable
  Icon getIcon(boolean isOpen);

  @NotNull
  String getText(@Nullable UsageView view);

  @Nullable
  FileStatus getFileStatus();

  boolean isValid();
  void update();

  @Override
  default boolean expandOnDoubleClick() {
    return !canNavigateToSource();
  }
}
