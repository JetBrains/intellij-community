// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;

/**
 * This interface describes visualization of attach items
 * @param <T> type of the child items (belonging to this group)
 * (applicable both for {@link XAttachHost} and {@link ProcessInfo} items)
 */
public interface XAttachPresentationGroup<T> extends Comparator<T> {
  /**
   * Define order among neighboring groups (smaller at first)
   */
  int getOrder();

  @NotNull @Nls
  String getGroupName();

  /**
   * @deprecated Use {@link #getItemIcon(Project, Object, UserDataHolder)} (will be removed in 2020.1)
   */
  @Deprecated(forRemoval = true)
  default @NotNull Icon getProcessIcon(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder) {
    throw new AbstractMethodError(getClass().getName() + " must implement getItemIcon method");
  }

  /**
   * @param dataHolder you may put your specific data into the holder at previous step in {@link XAttachDebuggerProvider#getAvailableDebuggers}
   *                   and use it for presentation
   * @return an icon to be shown in popup menu for your item, described by info
   */
  default @NotNull Icon getItemIcon(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder) {
    return getProcessIcon(project, info, dataHolder);
  }

  /**
   * @deprecated Use {@link #getItemDisplayText(Project, Object, UserDataHolder)} (will be removed in 2020.1)
   */
  @Deprecated(forRemoval = true)
  default @NotNull @Nls String getProcessDisplayText(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder) {
    throw new AbstractMethodError(getClass().getName() + " must implement getItemDisplayText method");
  }

  /**
   * @param dataHolder you may put your specific data into the holder at previous step in {@link XAttachDebuggerProvider#getAvailableDebuggers}
   *                   and use it for presentation
   * @return a text to be shown on your item, described by info
   */
  default @NotNull @Nls String getItemDisplayText(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder) {
    return getProcessDisplayText(project, info, dataHolder);
  }

  /**
   * @param dataHolder you may put your specific data into the holder at previous step in {@link XAttachDebuggerProvider#getAvailableDebuggers}
   *                   and use it for presentation
   * @return a description of process to be shown in tooltip of your item, described by info
   */
  default @Nullable @Nls String getItemDescription(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder) {
    return null;
  }
}
