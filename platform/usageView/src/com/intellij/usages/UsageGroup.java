// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.pom.Navigatable;
import com.intellij.ui.SimpleTextAttributes;
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

  default SimpleTextAttributes getTextAttributes(boolean isSelected) {
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  default void update() {
  }

  /**
   * Retrieves the clipping mode for the current UsageGroup.
   *
   * @return the clipping mode for the current object
   */
  default ClippingMode getClippingMode() {
    return ClippingMode.NO_CLIPPING;
  }

  /**
   * The ClippingMode enum represents the different strategies used for clipping strings.
   *
   * <p>The available modes are:
   * <ul>
   *   <li>{@link #NO_CLIPPING} - No clipping is required.</li>
   *   <li>{@link #NAME_CLIPPING} - Central parts of a string will be replaced with ellipsis when the name is too long.</li>
   *   <li>{@link #PATH_CLIPPING} - Directories from the central part of a path will be replaced one by one, until the necessary string length is reached.</li>
   * </ul>
   * </p>
   */
  enum ClippingMode {
    /**
     * The NO_CLIPPING mode is used when no cutting is clipping.
     */
    NO_CLIPPING,

    /**
     * The NAME_CLIPPING mode is used for cutting names. Central parts of string will be replaced with ellipsis when name is too long
     */
    NAME_CLIPPING,

    /**
     * The PATH_CLIPPING mode is used for cutting paths. Directories from the central part will be replaced one by one, until necessary
     * string length is reached
     */
    PATH_CLIPPING
  }
}
