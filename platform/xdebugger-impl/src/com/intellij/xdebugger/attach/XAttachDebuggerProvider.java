// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * This provider used to obtain debuggers, which can debug the process with specified {@link XAttachHost} and {@link ProcessInfo}
 */
public interface XAttachDebuggerProvider {
  ExtensionPointName<XAttachDebuggerProvider> EP = ExtensionPointName.create("com.intellij.xdebugger.attachDebuggerProvider");

  /**
   * will be removed in 2020.1, right after {@link XLocalAttachDebuggerProvider}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @NotNull
  static List<XAttachDebuggerProvider> getAttachDebuggerProviders() {
    return ContainerUtil.concat(new ArrayList<>(EP.getExtensionList()),
                                new ArrayList<>(XLocalAttachDebuggerProvider.EP.getExtensionList()));
  }

  /**
   * @return a group in which the supported processes should be visually organized.
   * Return {@link XDefaultLocalAttachGroup} for a common process group.
   */
  @NotNull
  default XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
    return XDefaultLocalAttachGroup.INSTANCE;
  }


  /**
   * @return if this XAttachDebuggerProvider is able to interact with this host
   */
  boolean isAttachHostApplicable(@NotNull XAttachHost attachHost);

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
