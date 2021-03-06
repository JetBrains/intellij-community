// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.ExecutionException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public abstract class VagrantSupport {
  @Nullable
  public static VagrantSupport getInstance() {
    return ApplicationManager.getApplication().getService(VagrantSupport.class);
  }

  public abstract ListenableFuture<RemoteCredentials> computeVagrantSettings(@Nullable Project project,
                                                                             @NotNull String vagrantFolder,
                                                                             @Nullable String machineName);

  public static void showMissingVagrantSupportMessage(final @Nullable Project project) {
    //noinspection DialogTitleCapitalization
    UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(project, IdeBundle.message("dialog.message.enable.vagrant.support.plugin"),
                                                              IdeBundle.message("dialog.title.vagrant.support.disabled")));
  }

  @NotNull
  public abstract RemoteCredentials getCredentials(@NotNull String vagrantFolder, @Nullable String machineName) throws IOException;

  public abstract boolean checkVagrantRunning(@NotNull String vagrantFolder, @Nullable String machineName, boolean askToRunIfDown);

  public abstract void runVagrant(@NotNull String vagrantFolder, @Nullable String machineName) throws ExecutionException;

  /**
   * @param vagrantFolder folder with Vagrantfile
   * @return path mappings from vagrant file
   */
  @Nullable
  public abstract PathMappingSettings getMappedFolders(@NotNull String vagrantFolder);

  public abstract Collection<? extends RemoteConnector> getVagrantInstancesConnectors(@NotNull Project project);

  public abstract boolean isVagrantInstance(VirtualFile dir);

  public abstract List<String> getMachineNames(@NotNull String instanceFolder);

  public boolean isNotReadyForSsh(Throwable t) {
    return isNotReadyForSsh(t.getMessage());
  }

  public static boolean isNotReadyForSsh(@NonNls @NotNull String errorMessage) {
    return errorMessage.contains("not yet ready for SSH");
  }

  @Nullable
  public abstract String findVagrantFolder(@NotNull Project project);


  public static class MultipleMachinesException extends Exception {
  }
}
