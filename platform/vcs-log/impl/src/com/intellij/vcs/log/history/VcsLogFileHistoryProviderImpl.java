// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcs.log.visible.filters.VcsLogFiltersKt;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.BiConsumer;

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
    VirtualFile root = assertNotNull(VcsLogUtil.getActualRoot(project, path));
    FilePath correctedPath = getCorrectedPath(project, path, root, revisionNumber);
    Hash hash = (revisionNumber != null) ? HashImpl.build(revisionNumber) : null;
    
    triggerFileHistoryUsage(path, hash);

    BiConsumer<AbstractVcsLogUi, Boolean> historyUiConsumer = (ui, firstTime) -> {
      if (hash != null) {
        ui.jumpToNearestCommit(hash, root);
      }
      else if (firstTime) {
        ui.jumpToRow(0);
      }
    };
    
    VcsLogManager logManager = assertNotNull(VcsProjectLog.getInstance(project).getLogManager());
    if (path.isDirectory() && VcsLogUtil.isFolderHistoryShownInLog()) {
      findOrOpenFolderHistory(project, logManager, root, correctedPath, hash, historyUiConsumer);
    }
    else {
      findOrOpenHistory(project, logManager, root, correctedPath, hash, historyUiConsumer);
    }
  }

  private static void triggerFileHistoryUsage(@NotNull FilePath path, @Nullable Hash hash) {
    String name = path.isDirectory() ? "Folder" : "File";
    String suffix = hash != null ? "ForRevision" : "";
    VcsLogUsageTriggerCollector.triggerUsage("Show" + name + "History" + suffix);
  }

  private static void findOrOpenHistory(@NotNull Project project, @NotNull VcsLogManager logManager,
                                        @NotNull VirtualFile root, @NotNull FilePath path, @Nullable Hash hash,
                                        @NotNull BiConsumer<AbstractVcsLogUi, Boolean> consumer) {
    FileHistoryUi fileHistoryUi = VcsLogContentUtil.findAndSelect(project, FileHistoryUi.class,
                                                                  ui -> ui.matches(path, hash));
    boolean firstTime = fileHistoryUi == null;
    if (firstTime) {
      String suffix = hash != null ? " (" + hash.toShortString() + ")" : "";
      fileHistoryUi = VcsLogContentUtil.openLogTab(project, logManager, TAB_NAME, path.getName() + suffix,
                                                   new FileHistoryUiFactory(path, root, hash), true);
    }

    consumer.accept(fileHistoryUi, firstTime);
  }

  private static void findOrOpenFolderHistory(@NotNull Project project, @NotNull VcsLogManager logManager,
                                              @NotNull VirtualFile root, @NotNull FilePath path, @Nullable Hash hash,
                                              @NotNull BiConsumer<AbstractVcsLogUi, Boolean> consumer) {
    VcsLogUiImpl ui = VcsLogContentUtil.findAndSelect(project, VcsLogUiImpl.class, logUi -> {
      return matches(logUi.getFilterUi().getFilters(), path, hash);
    });
    boolean firstTime = ui == null;
    if (firstTime) {
      VcsLogFilterCollection filters = createFilters(path, hash, root);
      ui = VcsProjectLog.getInstance(project).getTabsManager().openAnotherLogTab(logManager, filters);
      ui.getProperties().set(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES, true);
    }
    consumer.accept(ui, firstTime);
  }

  @NotNull
  private static VcsLogFilterCollection createFilters(@NotNull FilePath filePath, @Nullable Hash hash, @NotNull VirtualFile root) {
    VcsLogFilter pathFilter;
    if (Objects.equals(filePath.getVirtualFile(), root)) {
      pathFilter = VcsLogFilterObject.fromRoot(root);
    }
    else {
      pathFilter = VcsLogFilterObject.fromPaths(Collections.singleton(filePath));
    }
    if (hash == null) return VcsLogFilterObject.collection(pathFilter, VcsLogFilterObject.fromBranch(VcsLogUtil.HEAD));
    return VcsLogFilterObject.collection(pathFilter, VcsLogFilterObject.fromCommit(new CommitId(hash, root)));
  }

  private static boolean matches(@NotNull VcsLogFilterCollection filters, @NotNull FilePath filePath, @Nullable Hash hash) {
    VcsLogFilterCollection.FilterKey<?> hashKey = hash == null ? VcsLogFilterCollection.BRANCH_FILTER :
                                                  VcsLogFilterCollection.REVISION_FILTER;
    if (!VcsLogFiltersKt.matches(filters, hashKey, VcsLogFilterCollection.STRUCTURE_FILTER) &&
        !VcsLogFiltersKt.matches(filters, hashKey, VcsLogFilterCollection.ROOT_FILTER)) {
      return false;
    }
    if (!Objects.equals(getSingleFilePath(filters), filePath)) return false;
    if (hash != null) return Objects.equals(getSingleHash(filters), hash);
    return isFilteredByHead(filters);
  }

  private static boolean isFilteredByHead(@NotNull VcsLogFilterCollection filters) {
    VcsLogBranchFilter branchFilter = filters.get(VcsLogFilterCollection.BRANCH_FILTER);
    if (branchFilter == null) return false;
    return branchFilter.getTextPresentation().equals(Collections.singletonList(VcsLogUtil.HEAD));
  }

  @Nullable
  private static Hash getSingleHash(@NotNull VcsLogFilterCollection filters) {
    VcsLogRevisionFilter revisionFilter = filters.get(VcsLogFilterCollection.REVISION_FILTER);
    if (revisionFilter == null) return null;
    Collection<CommitId> heads = revisionFilter.getHeads();
    if (heads.size() != 1) return null;
    return assertNotNull(ContainerUtil.getFirstItem(heads)).getHash();
  }

  @Nullable
  private static FilePath getSingleFilePath(@NotNull VcsLogFilterCollection filters) {
    VcsLogStructureFilter structureFilter = filters.get(VcsLogFilterCollection.STRUCTURE_FILTER);
    if (structureFilter == null) {
      VcsLogRootFilter rootFilter = filters.get(VcsLogFilterCollection.ROOT_FILTER);
      if (rootFilter == null) return null;
      Collection<VirtualFile> roots = rootFilter.getRoots();
      if (roots.size() != 1) return null;
      return VcsUtil.getFilePath(assertNotNull(ContainerUtil.getFirstItem(roots)));
    }
    Collection<FilePath> filePaths = structureFilter.getFiles();
    if (filePaths.size() != 1) return null;
    return ContainerUtil.getFirstItem(filePaths);
  }

  @NotNull
  private static FilePath getCorrectedPath(@NotNull Project project, @NotNull FilePath path, @NotNull VirtualFile root,
                                           @Nullable String revisionNumber) {
    if (!root.equals(VcsUtil.getVcsRootFor(project, path)) && path.isDirectory()) {
      path = VcsUtil.getFilePath(path.getPath(), false);
    }

    if (revisionNumber == null) {
      return VcsUtil.getLastCommitPath(project, path);
    }

    return path;
  }
}
