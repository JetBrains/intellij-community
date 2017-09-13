/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface XAttachDebuggerProvider<T extends AttachToProcessSettings> {
  ExtensionPointName<XAttachDebuggerProvider> EP = ExtensionPointName.create("com.intellij.xdebugger.attachDebuggerProvider");

  /**
   * @return a group in which the supported processes should be visually organized.
   * Return XAttachGroup.DEFAULT for a common group.
   */
  @NotNull
  default XAttachGroup<T> getAttachGroup() {
    return XAttachGroup.DEFAULT;
  }

  /**
   * Attach to Process action invokes {@link #getAvailableDebuggers} method for every running process.
   * {@link XAttachDebuggerProvider} should return a list of the debuggers that can attach and debug a given process.
   * <p>
   * If there are several debuggers that can attach to a process, the user will have a choice between them.
   *
   * @param contextHolder use this data holder if you need to store temporary data during debuggers collection.
   *                      Lifetime of the data is restricted by a single Attach to Process action invocation.
   */
  @NotNull
  List<? extends XAttachDebugger<T>> getAvailableDebuggers(@NotNull Project project,
                                                           @NotNull T info,
                                                           @NotNull UserDataHolder contextHolder);
}
