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

import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface XLocalAttachDebuggerProvider extends  XAttachDebuggerProvider<XLocalAttachDebugger> {
  ExtensionPointName<XLocalAttachDebuggerProvider> EP = ExtensionPointName.create("com.intellij.xdebugger.localAttachDebuggerProvider");

  /**
   * @return a group in which the supported processes should be visually organized.
   *         Return XLocalAttachGroup.DEFAULT for a common group.
   *
   */
  @NotNull
  default XAttachGroup getAttachGroup() {
    return XAttachGroup.DEFAULT;
  }

  /**
   *  Attach to Local Process action invokes {@link #getAvailableDebuggers} method for every running process.
   *  {@link XLocalAttachDebuggerProvider} should return a list of the debuggers that can attach and debug a given process.
   *
   *  If there are several debuggers that can attach to a process, the user will have a choice between them.
   *
   * @param contextHolder use this data holder if you need to store temporary data during debuggers collection.
   *                      Lifetime of the data is restricted by a single Attach to Local Process action invocation.
   */
  @NotNull
  List<XLocalAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                   @NotNull ProcessInfo processInfo,
                                                   @NotNull UserDataHolder contextHolder);
}
