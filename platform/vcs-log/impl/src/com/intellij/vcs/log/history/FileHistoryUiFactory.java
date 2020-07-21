// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileHistoryUiFactory implements VcsLogManager.VcsLogUiFactory<FileHistoryUi> {
  @NotNull private final FilePath myFilePath;
  @NotNull private final VirtualFile myRoot;
  @Nullable private final Hash myHash;

  public FileHistoryUiFactory(@NotNull FilePath path, @NotNull VirtualFile root, @Nullable Hash hash) {
    myFilePath = path;
    myRoot = root;
    myHash = hash;
  }

  @Override
  public FileHistoryUi createLogUi(@NotNull Project project, @NotNull VcsLogData logData) {
    FileHistoryUiProperties properties = ServiceManager.getService(project, FileHistoryUiProperties.class);

    VcsLogFilterCollection filters =
      FileHistoryFilterer.createFilters(myFilePath, myHash, myRoot, properties.get(FileHistoryUiProperties.SHOW_ALL_BRANCHES));
    return new FileHistoryUi(logData, properties,
                             new VisiblePackRefresherImpl(project, logData,
                                                          filters,
                                                          PermanentGraph.SortType.Normal,
                                                          new FileHistoryFilterer(logData),
                                                          FileHistoryUi.getFileHistoryLogId(myFilePath, myHash)),
                             myFilePath, myHash, myRoot);
  }
}
