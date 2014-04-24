/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remote;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;

/**
 * @author traff
 */
public abstract class VagrantSupport {
  @Nullable
  public static VagrantSupport getInstance() {
    return ServiceManager.getService(VagrantSupport.class);
  }

  @Nullable
  public abstract Pair<String, RemoteCredentials> getVagrantSettings(Project project);

  @NotNull
  public abstract RemoteCredentials getVagrantSettings(@NotNull Project project, String vagrantFolder);

  @NotNull
  public abstract RemoteCredentials getCredentials(@NotNull String folder) throws IOException;

  public static void showMissingVagrantSupportMessage(final @Nullable Project project) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(project, "Enable Vagrant Support plugin",
                                 "Vagrant Support Disabled");
      }
    });
  }

  public abstract boolean checkVagrantAndRunIfDown(String folder);

  public abstract Collection<? extends RemoteConnector> getVagrantInstancesConnectors(@NotNull Project project);

  public abstract boolean isVagrantInstance(VirtualFile dir);
}
