// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * This provider used to obtain debuggers, which can debug the process with specified {@link XAttachHost} and {@link ProcessInfo}
 */
public interface XAttachDebuggerProvider {
  ExtensionPointName<XAttachDebuggerProvider> EP = ExtensionPointName.create("com.intellij.xdebugger.attachDebuggerProvider");

  /**
   * @return a group in which the supported processes should be visually organized.
   */
  @NotNull
  default XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
    return DEFAULT_PRESENTATION_GROUP;
  }

  XAttachProcessPresentationGroup DEFAULT_PRESENTATION_GROUP = new XAttachProcessPresentationGroup() {
    @Override
    public int getOrder() {
      return 0;
    }

    @Nls
    @Override
    public @NotNull String getGroupName() {
      return "";
    }

    @Override
    public @NotNull Icon getItemIcon(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
      return EmptyIcon.ICON_16;
    }

    @Nls
    @Override
    public @NotNull String getItemDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
      return info.getExecutableDisplayName();
    }
  };

  /**
   * @return if this XAttachDebuggerProvider is able to interact with this host
   */
  boolean isAttachHostApplicable(@NotNull XAttachHost attachHost);

  /**
   * Attach to Process action invokes {@code getAvailableDebuggers()} method for every running process on the given host.
   * The host is either local or selected by user among available ones (see {@link XAttachHostProvider})
   * <p>
   * If there are several debuggers that can attach to a process, the user will have a choice between them.
   *
   * @param contextHolder use this data holder if you need to store temporary data during debuggers collection.
   *                      Lifetime of the data is restricted by a single Attach to Process action invocation.
   * @param attachHost    host (environment) on which the process is being run
   * @param processInfo   process to attach to
   * @return a list of the debuggers that can attach and debug a given process
   */
  @NotNull
  List<XAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                              @NotNull XAttachHost attachHost,
                                              @NotNull ProcessInfo processInfo,
                                              @NotNull UserDataHolder contextHolder);
}
