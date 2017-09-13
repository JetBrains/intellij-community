/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@Deprecated
//use XAttachGroup<LocalAttachSettings>
public interface XLocalAttachGroup extends XAttachGroup<LocalAttachSettings> {
  @NotNull
  @Deprecated
    //use getIcon(@NotNull Project project, @NotNull LocalAttachSettings settings, @NotNull UserDataHolder dataHolder)
  Icon getIcon(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder);

  @NotNull
  @Override
  default Icon getIcon(@NotNull Project project, @NotNull LocalAttachSettings settings, @NotNull UserDataHolder dataHolder) {
    return getIcon(project, settings.getInfo(), dataHolder);
  }

  @NotNull
  @Deprecated
    //use getItemDisplayText(@NotNull Project project, @NotNull LocalAttachSettings settings, @NotNull UserDataHolder dataHolder)
  String getProcessDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder);

  @NotNull
  @Override
  default String getItemDisplayText(@NotNull Project project, @NotNull LocalAttachSettings settings, @NotNull UserDataHolder dataHolder) {
    return getProcessDisplayText(project, settings.getInfo(), dataHolder);
  }

  @Override
  default int compare(@NotNull Project project,
                      @NotNull LocalAttachSettings a,
                      @NotNull LocalAttachSettings b,
                      @NotNull UserDataHolder dataHolder) {
    return compare(project, a.getInfo(), b.getInfo(), dataHolder);
  }

  @Deprecated
    //use int compare(@NotNull Project project, @NotNull LocalAttachSettings a, @NotNull LocalAttachSettings b, @NotNull UserDataHolder dataHolder)
  int compare(@NotNull Project project, @NotNull ProcessInfo a, @NotNull ProcessInfo b, @NotNull UserDataHolder dataHolder);

  XAttachGroup DEFAULT = new XDefaultAttachGroup<LocalAttachSettings>();
}
