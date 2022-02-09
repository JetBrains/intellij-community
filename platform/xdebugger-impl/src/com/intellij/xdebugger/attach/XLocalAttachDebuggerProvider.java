// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated use {@link XAttachDebuggerProvider} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public interface XLocalAttachDebuggerProvider extends XAttachDebuggerProvider {
  ExtensionPointName<XAttachDebuggerProvider> EP = ExtensionPointName.create("com.intellij.xdebugger.localAttachDebuggerProvider");

  /**
   * @deprecated use {@link XAttachDebuggerProvider#getAvailableDebuggers(Project, XAttachHost, ProcessInfo, UserDataHolder)} instead
   */
  @Deprecated
  List<XLocalAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                   @NotNull ProcessInfo processInfo,
                                                   @NotNull UserDataHolder contextHolder);

  /**
   * @deprecated use {@link XAttachDebuggerProvider#getPresentationGroup()} instead
   */
  @Deprecated
  @NotNull
  default XAttachPresentationGroup<ProcessInfo> getAttachGroup() {
    return XDefaultLocalAttachGroup.INSTANCE;
  }

  @NotNull
  @Override
  default XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
    return getAttachGroup();
  }

  @Override
  default boolean isAttachHostApplicable(@NotNull XAttachHost attachHost) {
    return attachHost instanceof LocalAttachHost;
  }

  @NotNull
  @Override
  default List<XAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                      @NotNull XAttachHost attachHost,
                                                      @NotNull ProcessInfo processInfo,
                                                      @NotNull UserDataHolder contextHolder) {
    assert attachHost instanceof LocalAttachHost;

    return new ArrayList<>(getAvailableDebuggers(project, processInfo, contextHolder));
  }
}
