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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class FileHistoryUiFactory implements VcsLogManager.VcsLogUiFactory<FileHistoryUi> {
  @NotNull private final FilePath myFilePath;
  @Nullable private final Hash myHash;

  public FileHistoryUiFactory(@NotNull FilePath path, @Nullable Hash hash) {
    myFilePath = path;
    myHash = hash;
  }

  @Override
  public FileHistoryUi createLogUi(@NotNull Project project, @NotNull VcsLogData logData) {
    FileHistoryUiProperties properties = ServiceManager.getService(project, FileHistoryUiProperties.class);
    VirtualFile root = ObjectUtils.assertNotNull(VcsUtil.getVcsRootFor(project, myFilePath));
    VcsLogFilterCollection filters =
      FileHistoryFilterer.createFilters(myFilePath, myHash, root, properties.get(FileHistoryUiProperties.SHOW_ALL_BRANCHES));
    return new FileHistoryUi(logData, new VcsLogColorManagerImpl(Collections.singleton(root)), properties,
                             new VisiblePackRefresherImpl(project, logData,
                                                          filters,
                                                          PermanentGraph.SortType.Normal,
                                                          new FileHistoryFilterer(logData),
                                                          FileHistoryUi.getFileHistoryLogId(myFilePath, myHash)) {
                               @Override
                               public void onRefresh() {
                                 // this is a hack here:
                                 // file history for a file does not use non-full data packs
                                 // so no reason to interrupt it with a new pack
                                 DataPack pack = logData.getDataPack();
                                 if (!myFilePath.isDirectory() && pack != DataPack.EMPTY && !pack.isFull()) return;
                                 super.onRefresh();
                               }
                             }, myFilePath, myHash, root);
  }
}
