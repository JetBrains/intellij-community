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
package com.intellij.vcs.log.history;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFileHistoryProvider;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsProjectLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class VcsLogFileHistoryProviderImpl implements VcsLogFileHistoryProvider {
  @NotNull
  public static final String TAB_NAME = "History";

  @Override
  public boolean canShowFileHistory(@NotNull Project project, @NotNull FilePath path) {
    if (!Registry.is("vcs.new.history")) return false;

    VcsRoot rootObject = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(path);
    if (rootObject == null) return false;

    VirtualFile root = rootObject.getPath();
    AbstractVcs vcs = rootObject.getVcs();
    if (vcs == null || root == null) return false;

    VcsLogData dataManager = VcsProjectLog.getInstance(project).getDataManager();
    if (dataManager == null || !dataManager.getRoots().contains(root) || dataManager.getIndex().getDataGetter() == null) return false;

    List<VcsLogProvider> allLogProviders = Arrays.asList(Extensions.getExtensions(VcsLogProvider.LOG_PROVIDER_EP, project));
    VcsLogProvider provider = ContainerUtil.find(allLogProviders, p -> p.getSupportedVcs().equals(vcs.getKeyInstanceMethod()));
    if (provider == null) return false;

    return VcsLogProperties.get(provider, VcsLogProperties.SUPPORTS_INDEXING);
  }

  @Override
  public void showFileHistory(@NotNull Project project, @NotNull FilePath path, @Nullable String revisionNumber) {
    if (!VcsLogContentUtil.findAndSelectContent(project, FileHistoryUi.class, ui -> ui.getPath().equals(path))) {
      VcsLogManager logManager = VcsProjectLog.getInstance(project).getLogManager();
      assert logManager != null;
      VcsLogContentUtil.openLogTab(project, logManager, TAB_NAME, path.getName(), new FileHistoryUiFactory(path));
    }
  }
}
