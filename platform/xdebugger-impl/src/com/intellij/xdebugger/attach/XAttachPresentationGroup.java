/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.attach;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;

/**
 * This interface describes visualization of attach items
 * @param <T> type of the child items (belonging to this group)
 * (applicable both for {@link XAttachHost} and {@link ProcessInfo} items)
 */
@ApiStatus.Experimental
public interface XAttachPresentationGroup<T> extends Comparator<T> {
  /**
   * Define order among neighboring groups (smaller at first)
   */
  int getOrder();

  @NotNull
  String getGroupName();

  /**
   * @deprecated Use {@link #getItemIcon(Project, Object, UserDataHolder)} (will be removed in 2018.2)
   */
  @Deprecated
  @NotNull
  Icon getProcessIcon(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder);

  /**
   * @param dataHolder you may put your specific data into the holder at previous step in {@link XAttachDebuggerProvider#getAvailableDebuggers}
   *                   and use it for presentation
   * @return an icon to be shown in popup menu for your item, described by info
   */
  @NotNull
  default Icon getItemIcon(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder) {
    return getProcessIcon(project, info, dataHolder);
  }

  /**
   * @deprecated Use {@link #getItemDisplayText(Project, Object, UserDataHolder)} (will be removed in 2018.2)
   */
  @Deprecated
  @NotNull
  String getProcessDisplayText(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder);

  /**
   * @param dataHolder you may put your specific data into the holder at previous step in {@link XAttachDebuggerProvider#getAvailableDebuggers}
   *                   and use it for presentation
   * @return a text to be shown on your item, described by info
   */
  @NotNull
  default String getItemDisplayText(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder) {
    return getProcessDisplayText(project, info, dataHolder);
  }

  /**
   * @param dataHolder you may put your specific data into the holder at previous step in {@link XAttachDebuggerProvider#getAvailableDebuggers}
   *                   and use it for presentation
   * @return a description of process to be shown in tooltip of your item, described by info
   */
  @Nullable
  default String getItemDescription(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder) {
    return null;
  }

  /**
   * @deprecated use {@link #compare(Object, Object)} (will be removed in 2018.2)
   *
   * Specifies process order in your group
   *
   * @param dataHolder you may put your specific data into the holder at previous step in {@link XAttachDebuggerProvider#getAvailableDebuggers}
   *                   and use it for comparison
   */
  @Deprecated
  default int compare(@NotNull Project project, @NotNull T a, @NotNull T b, @NotNull UserDataHolder dataHolder) {
    return compare(a, b);
  }
}
