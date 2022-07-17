// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.pom.Navigatable;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface UsageGroup extends Comparable<UsageGroup>, Navigatable {

  /**
   * @deprecated implement {@link #getIcon()} instead
   */
  @Deprecated(forRemoval = true)
  default @Nullable Icon getIcon(boolean isOpen) {
    if (ReflectionUtil.getMethodDeclaringClass(getClass(), "getIcon") == UsageGroup.class) {
      return null;
    }
    return getIcon(); // getIcon() is implemented
  }

  default @Nullable Icon getIcon() {
    return getIcon(true);
  }

  /**
   * @deprecated implement {@link #getPresentableGroupText()} instead
   */
  @Deprecated(forRemoval = true)
  default @ListItem @NotNull String getText(@Nullable UsageView view) {
    if (ReflectionUtil.getMethodDeclaringClass(getClass(), "getPresentableGroupText") == UsageGroup.class) {
      throw new AbstractMethodError("getPresentableGroupText() must be implemented");
    }
    return getPresentableGroupText(); // getPresentableGroupText() is implemented
  }

  default @ListItem @NotNull String getPresentableGroupText() {
    return getText(null);
  }

  default @Nullable FileStatus getFileStatus() {
    return null;
  }

  default boolean isValid() {
    return true;
  }

  default void update() {
  }
}
