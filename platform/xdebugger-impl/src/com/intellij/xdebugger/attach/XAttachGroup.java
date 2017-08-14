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

import javax.swing.*;

@ApiStatus.Experimental
public interface XAttachGroup<T> {
  int getOrder();

  @NotNull
  String getGroupName();

  /**
   * @param dataHolder you may put your specific data into the holder at previous step in method @{@link XAttachDebuggerProvider#getAvailableDebuggers(Project, ProcessInfo, UserDataHolder)}
   * and use it for presentation
   * @return an icon to be shown in popup menu for your debugger item
   */
  @NotNull
  Icon getIcon(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder);

  /**
   * @param dataHolder you may put your specific data into the holder at previous step in method @{@link XAttachDebuggerProvider#getAvailableDebuggers(Project, ProcessInfo, UserDataHolder)}
   * and use it for presentation
   * @return a text to be shown on your debugger item
   */
  @NotNull
  String getItemDisplayText(@NotNull Project project, @NotNull T info, @NotNull UserDataHolder dataHolder);

  /**
   * Specifies process order in your group
   * @param dataHolder you may put your specific data into the holder at previous step in method @{@link XAttachDebuggerProvider#getAvailableDebuggers(Project, ProcessInfo, UserDataHolder)}
   * and use it for comparison
   */
  int compare(@NotNull Project project, @NotNull T a, @NotNull T b, @NotNull UserDataHolder dataHolder);

  XAttachGroup DEFAULT = new XDefaultAttachGroup();
}
