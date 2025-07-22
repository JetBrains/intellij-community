// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class FileHistoryUiFactory implements VcsLogManager.VcsLogUiFactory<FileHistoryUi> {
  private final @NotNull FilePath myFilePath;
  private final @NotNull VirtualFile myRoot;
  private final @Nullable Hash myHash;

  FileHistoryUiFactory(@NotNull FilePath path, @NotNull VirtualFile root, @Nullable Hash hash) {
    myFilePath = path;
    myRoot = root;
    myHash = hash;
  }

  @Override
  public @NotNull FileHistoryUi createLogUi(@NotNull Project project, @NotNull VcsLogData logData) {
    FileHistoryUiProperties properties = project.getService(FileHistoryUiProperties.class);

    VcsLogFilterCollection filters = FileHistoryFilterer.createFilters(myFilePath, myHash, myRoot);
    String logId = FileHistoryUi.getFileHistoryLogId(myFilePath, myHash);
    VisiblePackRefresherImpl visiblePackRefresher = new VisiblePackRefresherImpl(project, logData, filters, PermanentGraph.Options.Default,
                                                                                 new FileHistoryFilterer(logData, logId), logId);
    VcsLogProvider provider = Objects.requireNonNull(logData.getLogProviders().get(myRoot));
    FileHistoryUi ui = new FileHistoryUi(logData, properties, visiblePackRefresher, myFilePath, myHash, myRoot, logId, filters,
                                         Objects.requireNonNull(provider.getDiffHandler()));

    RegistryValueListener registryValueListener = new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        visiblePackRefresher.onRefresh();
      }
    };
    FileHistoryBuilder.refineValue.addListener(registryValueListener, ui);
    FileHistoryBuilder.removeTrivialMergesValue.addListener(registryValueListener, ui);

    return ui;
  }
}
