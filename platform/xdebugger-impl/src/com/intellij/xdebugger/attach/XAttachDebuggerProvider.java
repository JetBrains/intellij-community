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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.sun.tools.javac.util.ArrayUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * This provider used to obtain debuggers, which can debug the process with specified {@link XAttachHost} and {@link ProcessInfo}
 */
@ApiStatus.Experimental
public interface XAttachDebuggerProvider {
  ExtensionPointName<XAttachDebuggerProvider> EP = ExtensionPointName.create("com.intellij.xdebugger.attachDebuggerProvider");

  static XAttachDebuggerProvider[] getAttachDebuggerProviders() {
    List<XAttachDebuggerProvider> providersList = ContainerUtil.newArrayList(Extensions.getExtensions(EP));
    List<XAttachDebuggerProvider> localProvidersList = ContainerUtil.newArrayList(Extensions.getExtensions(XLocalAttachDebuggerProvider.EP));

    providersList.addAll(localProvidersList);

    XAttachDebuggerProvider[] providers = new XAttachDebuggerProvider[providersList.size()];
    return providersList.toArray(providers);
  }

  /**
   * @return a group in which the supported processes should be visually organized.
   * Return XAttachPresentationGroup.DEFAULT for a common process group.
   */
  @NotNull
  default XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
    return XAttachPresentationGroup.DEFAULT;
  }


  /**
   * @return if this XAttachDebuggerProvider is able to interact with this host
   */
  default boolean isAttachHostApplicable(@NotNull XAttachHost attachHost) {
    return attachHost instanceof LocalAttachHost;
  }

  /**
   * Attach to Process action invokes {@link #getAvailableDebuggers} method for every running process on the given host.
   * The host is either local or selected by user among available ones (see {@link XAttachHostProvider})
   * <p>
   * If there are several debuggers that can attach to a process, the user will have a choice between them.
   *
   * @param contextHolder use this data holder if you need to store temporary data during debuggers collection.
   *                      Lifetime of the data is restricted by a single Attach to Process action invocation.
   * @param hostInfo host (environment) on which the process is being run
   * @param process process to attach to
   * @return a list of the debuggers that can attach and debug a given process
   */
  @NotNull
  List<XAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                              @NotNull XAttachHost hostInfo,
                                              @NotNull ProcessInfo process,
                                              @NotNull UserDataHolder contextHolder);
}
