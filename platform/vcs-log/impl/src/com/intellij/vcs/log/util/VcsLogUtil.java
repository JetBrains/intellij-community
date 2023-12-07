// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.CommittedChangeListForRevision;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.CompressedRefs;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcsUtil.VcsUtil;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.vcs.log.impl.VcsLogManager.findLogProviders;
import static java.util.Collections.singletonList;

public final class VcsLogUtil {
  public static final int MAX_SELECTED_COMMITS = 1000;
  public static final int FULL_HASH_LENGTH = 40;
  public static final int SHORT_HASH_LENGTH = 8;
  public static final Pattern HASH_REGEX = Pattern.compile("[a-fA-F0-9]{7,40}");
  public static final Pattern HASH_PREFIX_REGEX = Pattern.compile("[a-fA-F0-9]{4,40}");
  public static final @NlsSafe String HEAD = "HEAD";

  public static @NotNull Map<VirtualFile, Set<VcsRef>> groupRefsByRoot(@NotNull Collection<? extends VcsRef> refs) {
    Map<VirtualFile, Set<VcsRef>> map = new TreeMap<>(Comparator.comparing(VirtualFile::getPresentableUrl));
    for (VcsRef item : refs) {
      map.computeIfAbsent(item.getRoot(), k -> new HashSet<>()).add(item);
    }
    return map;
  }

  public static int compareRoots(@NotNull VirtualFile root1, @NotNull VirtualFile root2) {
    return root1.getPresentableUrl().compareTo(root2.getPresentableUrl());
  }

  private static @NotNull Set<VirtualFile> collectRoots(@NotNull Collection<? extends FilePath> files, @NotNull Set<? extends VirtualFile> roots) {
    Set<VirtualFile> selectedRoots = new HashSet<>();

    List<VirtualFile> sortedRoots = ContainerUtil.sorted(roots, Comparator.comparing(VirtualFile::getPath));

    for (FilePath filePath : files) {
      VirtualFile virtualFile = filePath.getVirtualFile();

      if (virtualFile != null && roots.contains(virtualFile)) {
        // if a root itself is selected, add this root
        selectedRoots.add(virtualFile);
      }
      else {
        VirtualFile candidateAncestorRoot = null;
        for (VirtualFile root : sortedRoots) {
          if (FileUtil.isAncestor(VfsUtilCore.virtualToIoFile(root), filePath.getIOFile(), false)) {
            candidateAncestorRoot = root;
          }
        }
        if (candidateAncestorRoot != null) {
          selectedRoots.add(candidateAncestorRoot);
        }
      }

      // add all roots under selected path
      if (virtualFile != null) {
        for (VirtualFile root : roots) {
          if (VfsUtilCore.isAncestor(virtualFile, root, false)) {
            selectedRoots.add(root);
          }
        }
      }
    }

    return selectedRoots;
  }

  public static @NotNull Set<VirtualFile> getVisibleRoots(@NotNull VcsLogUi logUi) {
    VcsLogFilterCollection filters = logUi.getFilterUi().getFilters();
    Set<VirtualFile> roots = logUi.getDataPack().getLogProviders().keySet();
    return getAllVisibleRoots(roots, filters);
  }

  public static @NotNull Set<VirtualFile> getAllVisibleRoots(@NotNull Collection<VirtualFile> roots,
                                                             @NotNull VcsLogFilterCollection filters) {
    return getAllVisibleRoots(roots, filters.get(VcsLogFilterCollection.ROOT_FILTER), filters.get(VcsLogFilterCollection.STRUCTURE_FILTER));
  }

  // collect absolutely all roots that might be visible
  // if filters unset returns just all roots
  public static @NotNull Set<VirtualFile> getAllVisibleRoots(@NotNull Collection<VirtualFile> roots,
                                                             @Nullable VcsLogRootFilter rootFilter,
                                                             @Nullable VcsLogStructureFilter structureFilter) {
    if (rootFilter == null && structureFilter == null) return new HashSet<>(roots);

    Collection<VirtualFile> fromRootFilter;
    if (rootFilter != null) {
      fromRootFilter = rootFilter.getRoots();
    }
    else {
      fromRootFilter = roots;
    }

    Collection<VirtualFile> fromStructureFilter;
    if (structureFilter != null) {
      fromStructureFilter = collectRoots(structureFilter.getFiles(), new HashSet<>(roots));
    }
    else {
      fromStructureFilter = roots;
    }

    return new HashSet<>(ContainerUtil.intersection(fromRootFilter, fromStructureFilter));
  }

  // for given root returns files that are selected in it,
  // if a root is visible as a whole returns empty set
  // same if root is invisible as a whole
  // so check that before calling this method
  public static @NotNull Set<FilePath> getFilteredFilesForRoot(final @NotNull VirtualFile root, @NotNull VcsLogFilterCollection filterCollection) {
    VcsLogStructureFilter structureFilter = filterCollection.get(VcsLogFilterCollection.STRUCTURE_FILTER);
    if (structureFilter == null) return Collections.emptySet();
    Collection<FilePath> files = structureFilter.getFiles();

    return new HashSet<>(ContainerUtil.filter(files, filePath -> {
      VirtualFile virtualFile = filePath.getVirtualFile();
      return root.equals(virtualFile) || FileUtil.isAncestor(VfsUtilCore.virtualToIoFile(root), filePath.getIOFile(), false);
    }));
  }

  public static @Nullable @NlsSafe String getSingleFilteredBranch(@NotNull VcsLogFilterCollection filters, @NotNull VcsLogRefs refs) {
    VcsLogBranchFilter filter = filters.get(VcsLogFilterCollection.BRANCH_FILTER);
    if (filter == null) return null;

    String branchName = null;
    Set<VirtualFile> checkedRoots = new HashSet<>();
    for (VcsRef branch : refs.getBranches()) {
      if (!filter.matches(branch.getName())) continue;

      if (branchName == null) {
        branchName = branch.getName();
      }
      else if (!branch.getName().equals(branchName)) {
        return null;
      }

      if (checkedRoots.contains(branch.getRoot())) return null;
      checkedRoots.add(branch.getRoot());
    }

    return branchName;
  }

  public static boolean isRegexp(@NotNull String text) {
    return StringUtil.containsAnyChar(text, "()[]{}.*?+^$\\|");
  }

  public static @NotNull TextRevisionNumber convertToRevisionNumber(@NotNull Hash hash) {
    return new TextRevisionNumber(hash.asString(), hash.toShortString());
  }

  public static @NotNull List<? extends VcsFullCommitDetails> getDetails(@NotNull VcsLogProvider logProvider,
                                                                         @NotNull VirtualFile root,
                                                                         @NotNull List<String> hashes) throws VcsException {
    List<VcsFullCommitDetails> result = new ArrayList<>();
    logProvider.readFullDetails(root, hashes, result::add);
    return result;
  }

  public static @NotNull CommittedChangeListForRevision createCommittedChangeList(@NotNull VcsFullCommitDetails detail, boolean withChanges) {
    return new CommittedChangeListForRevision(detail.getSubject(), detail.getFullMessage(),
                                              VcsUserUtil.getShortPresentation(detail.getCommitter()),
                                              new Date(detail.getCommitTime()),
                                              withChanges ? detail.getChanges() : ContainerUtil.emptyList(),
                                              convertToRevisionNumber(detail.getId()));
  }

  public static @NotNull CommittedChangeListForRevision createCommittedChangeList(@NotNull VcsFullCommitDetails detail) {
    return createCommittedChangeList(detail, true);
  }

  public static @NotNull @NlsSafe String getShortHash(@NotNull String hashString) {
    return getShortHash(hashString, SHORT_HASH_LENGTH);
  }

  public static @NotNull @NlsSafe String getShortHash(@NotNull String hashString, int shortHashLength) {
    return hashString.substring(0, Math.min(shortHashLength, hashString.length()));
  }

  public static boolean isFullHash(@NotNull String s) {
    return s.length() == FULL_HASH_LENGTH && HASH_REGEX.matcher(s).matches();
  }

  public static @Nullable VcsRef findBranch(@NotNull RefsModel refs, @NotNull VirtualFile root, @NotNull String branchName) {
    CompressedRefs compressedRefs = refs.getAllRefsByRoot().get(root);
    if (compressedRefs == null) return null;
    Stream<VcsRef> branches = compressedRefs.streamBranches();
    return branches.filter(vcsRef -> vcsRef.getName().equals(branchName)).findFirst().orElse(null);
  }

  public static @NotNull List<Change> collectChanges(@NotNull List<? extends VcsFullCommitDetails> detailsList) {
    List<Change> changes = new ArrayList<>();
    List<VcsFullCommitDetails> detailsListReversed = ContainerUtil.reverse(detailsList);
    for (VcsFullCommitDetails details : detailsListReversed) {
      changes.addAll(details.getChanges());
    }

    return CommittedChangesTreeBrowser.zipChanges(changes);
  }

  public static @Nullable VirtualFile getActualRoot(@NotNull Project project, @NotNull FilePath path) {
    VcsRoot rootObject = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(path);
    if (rootObject == null) return null;
    Map<VirtualFile, VcsLogProvider> providers = findLogProviders(singletonList(rootObject), project);
    if (providers.isEmpty()) return null;
    VcsLogProvider provider = Objects.requireNonNull(getFirstItem(providers.values()));
    return provider.getVcsRoot(project, rootObject.getPath(), path);
  }

  public static @Nullable VirtualFile getActualRoot(@NotNull Project project,
                                                    @NotNull Map<VirtualFile, VcsLogProvider> providers,
                                                    @NotNull FilePath path) {
    List<VirtualFile> sortedRoots = ContainerUtil.sorted(providers.keySet(), Comparator.comparing(VirtualFile::getPath).reversed());
    VirtualFile root = ContainerUtil.find(sortedRoots, r -> FileUtil.isAncestor(VfsUtilCore.virtualToIoFile(r), path.getIOFile(), false));
    if (root == null) return null;
    VcsLogProvider provider = providers.get(root);
    if (provider == null) return null;
    return provider.getVcsRoot(project, root, path);
  }

  public static @Nullable Collection<FilePath> getAffectedPaths(@NotNull VcsLogUi logUi) {
    return getAffectedPaths(logUi.getDataPack());
  }

  public static @Nullable Collection<FilePath> getAffectedPaths(@NotNull VcsLogDataPack dataPack) {
    VcsLogStructureFilter structureFilter = dataPack.getFilters().get(VcsLogFilterCollection.STRUCTURE_FILTER);
    if (structureFilter != null) {
      return structureFilter.getFiles();
    }
    return null;
  }

  public static @Nullable Collection<FilePath> getAffectedPaths(@NotNull VirtualFile root, @NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties != null && properties.exists(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES)) {
      if (properties.get(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES)) {
        VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
        Project project = e.getProject();
        if (logUi != null && project != null) {
          Collection<FilePath> affectedFilePaths = getAffectedPaths(logUi);
          if (affectedFilePaths != null) {
            return ContainerUtil.filter(affectedFilePaths,
                                        path -> Objects.equals(VcsUtil.getVcsRootFor(project, path), root));
          }
        }
      }
    }
    return null;
  }

  public static int getMaxSize(@NotNull List<? extends VcsFullCommitDetails> detailsList) {
    int maxSize = 0;
    for (VcsFullCommitDetails details : detailsList) {
      maxSize = Math.max(getSize(details), maxSize);
    }
    return maxSize;
  }

  public static int getSize(@NotNull VcsFullCommitDetails details) {
    if (details instanceof VcsChangesLazilyParsedDetails) {
      return ((VcsChangesLazilyParsedDetails)details).size();
    }

    int size = 0;
    for (int i = 0; i < details.getParents().size(); i++) {
      size += details.getChanges(i).size();
    }
    return size;
  }

  public static int getShownChangesLimit() {
    return Registry.intValue("vcs.log.max.changes.shown");
  }

  public static @NotNull @NonNls String getSizeText(int maxSize) {
    if (maxSize < 1000) {
      return String.valueOf(maxSize);
    }
    DecimalFormat format = new DecimalFormat("#.#");
    format.setRoundingMode(RoundingMode.FLOOR);
    if (maxSize < 10_000) {
      return format.format(maxSize / 1000.0) + "K";
    }
    else if (maxSize < 1_000_000) {
      return (maxSize / 1000) + "K";
    }
    else if (maxSize < 10_000_000) {
      return format.format(maxSize / 1_000_000.0) + "M";
    }
    return (maxSize / 1_000_000) + "M";
  }

  public static @NotNull @NonNls String getProvidersMapText(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
    return "[" + StringUtil.join(providers.keySet(), file -> file.getPresentableUrl(), ", ") + "]";
  }

  public static @NotNull @Nls String getVcsDisplayName(@NotNull Project project, @NotNull Collection<? extends VcsLogProvider> logProviders) {
    Set<AbstractVcs> vcs = ContainerUtil.map2SetNotNull(logProviders,
                                                        provider -> VcsUtil.findVcsByKey(project, provider.getSupportedVcs()));
    if (vcs.size() != 1) return VcsLogBundle.message("vcs");
    return Objects.requireNonNull(getFirstItem(vcs)).getDisplayName();
  }

  public static @NotNull @Nls String getVcsDisplayName(@NotNull Project project, @NotNull VcsLogManager logManager) {
    return getVcsDisplayName(project, logManager.getDataManager().getLogProviders().values());
  }

  public static void invokeOnChange(@NotNull VcsLogUi ui, @NotNull Runnable runnable,
                                    @NotNull Condition<? super VcsLogDataPack> condition) {
    ui.addLogListener(new VcsLogListener() {
      @Override
      public void onChange(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
        if (condition.value(dataPack)) {
          runnable.run();
          ui.removeLogListener(this);
        }
      }
    });
  }

  public static void runWhenVcsAndLogIsReady(@NotNull Project project, @NotNull Consumer<? super VcsLogManager> action) {
    VcsLogManager logManager = VcsProjectLog.getInstance(project).getLogManager();
    if (logManager != null) {
      action.consume(logManager);
      return;
    }
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization(() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        VcsProjectLog.Companion.runWhenLogIsReady(project, manager -> {
          action.consume(manager);
          return Unit.INSTANCE;
        });
      }, project.getDisposed());
    });
  }
}
