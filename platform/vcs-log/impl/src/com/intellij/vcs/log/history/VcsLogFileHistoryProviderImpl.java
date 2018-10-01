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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogFileHistoryProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class VcsLogFileHistoryProviderImpl implements VcsLogFileHistoryProvider {
  @NotNull
  public static final String TAB_NAME = "History";

  @Override
  public boolean canShowFileHistory(@NotNull Project project, @NotNull FilePath path) {
    if (!Registry.is("vcs.new.history")) return false;

    VirtualFile root = VcsLogUtil.getActualRoot(project, path);
    if (root == null) return false;

    VcsLogData dataManager = VcsProjectLog.getInstance(project).getDataManager();
    if (dataManager == null) return false;

    return dataManager.getIndex().isIndexingEnabled(root);
  }

  @Override
  public void showFileHistory(@NotNull Project project, @NotNull FilePath path, @Nullable String revisionNumber) {
    FilePath correctedPath = getCorrectedPath(project, path, revisionNumber);

    Hash hash = (revisionNumber != null) ? HashImpl.build(revisionNumber) : null;
    FileHistoryUi fileHistoryUi = VcsLogContentUtil.findAndSelect(project, FileHistoryUi.class,
                                                                  ui -> ui.matches(correctedPath, hash));
    boolean firstTime = fileHistoryUi == null;
    if (firstTime) {
      VcsLogManager logManager = VcsProjectLog.getInstance(project).getLogManager();
      assert logManager != null;
      String suffix = hash != null ? " (" + hash.toShortString() + ")" : "";
      fileHistoryUi = VcsLogContentUtil.openLogTab(project, logManager, TAB_NAME, correctedPath.getName() + suffix,
                                                   new FileHistoryUiFactory(correctedPath, hash), true);
    }

    if (hash != null) {
      fileHistoryUi.jumpToNearestCommit(hash);
    }
    else if (firstTime) {
      fileHistoryUi.jumpToRow(0);
    }
  }

  @NotNull
  private static FilePath getCorrectedPath(@NotNull Project project, @NotNull FilePath path, @Nullable String revisionNumber) {
    VirtualFile root = assertNotNull(VcsLogUtil.getActualRoot(project, path));
    if (!root.equals(VcsUtil.getVcsRootFor(project, path)) && path.isDirectory()) {
      path = VcsUtil.getFilePath(path.getPath(), false);
    }

    if (revisionNumber == null) {
      return VcsUtil.getLastCommitPath(project, path);
    }

    return path;
  }
}
