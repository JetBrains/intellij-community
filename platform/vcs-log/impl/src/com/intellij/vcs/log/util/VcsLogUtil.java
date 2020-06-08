// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.CommittedChangeListForRevision;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.CompressedRefs;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcsUtil.VcsUtil;
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
  @NonNls public static final String HEAD = "HEAD";

  @NotNull
  public static Map<VirtualFile, Set<VcsRef>> groupRefsByRoot(@NotNull Collection<? extends VcsRef> refs) {
    return groupByRoot(refs, VcsRef::getRoot);
  }

  @NotNull
  private static <T> Map<VirtualFile, Set<T>> groupByRoot(@NotNull Collection<? extends T> items,
                                                          @NotNull Function<? super T, ? extends VirtualFile> rootGetter) {
    Map<VirtualFile, Set<T>> map = new TreeMap<>(Comparator.comparing(VirtualFile::getPresentableUrl));
    for (T item : items) {
      VirtualFile root = rootGetter.fun(item);
      map.computeIfAbsent(root, k -> new HashSet<>()).add(item);
    }
    return map;
  }

  public static int compareRoots(@NotNull VirtualFile root1, @NotNull VirtualFile root2) {
    return root1.getPresentableUrl().compareTo(root2.getPresentableUrl());
  }

  @NotNull
  private static Set<VirtualFile> collectRoots(@NotNull Collection<? extends FilePath> files, @NotNull Set<? extends VirtualFile> roots) {
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

  @NotNull
  public static Set<VirtualFile> getVisibleRoots(@NotNull VcsLogUi logUi) {
    VcsLogFilterCollection filters = logUi.getFilterUi().getFilters();
    Set<VirtualFile> roots = logUi.getDataPack().getLogProviders().keySet();
    return getAllVisibleRoots(roots, filters);
  }

  @NotNull
  public static Set<VirtualFile> getAllVisibleRoots(@NotNull Collection<VirtualFile> roots,
                                                    @NotNull VcsLogFilterCollection filters) {
    return getAllVisibleRoots(roots, filters.get(VcsLogFilterCollection.ROOT_FILTER), filters.get(VcsLogFilterCollection.STRUCTURE_FILTER));
  }

  // collect absolutely all roots that might be visible
  // if filters unset returns just all roots
  @NotNull
  public static Set<VirtualFile> getAllVisibleRoots(@NotNull Collection<VirtualFile> roots,
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

  // for given root returns files that are selected in it
  // if a root is visible as a whole returns empty set
  // same if root is invisible as a whole
  // so check that before calling this method
  @NotNull
  public static Set<FilePath> getFilteredFilesForRoot(@NotNull final VirtualFile root, @NotNull VcsLogFilterCollection filterCollection) {
    VcsLogStructureFilter structureFilter = filterCollection.get(VcsLogFilterCollection.STRUCTURE_FILTER);
    if (structureFilter == null) return Collections.emptySet();
    Collection<FilePath> files = structureFilter.getFiles();

    return new HashSet<>(ContainerUtil.filter(files, filePath -> {
      VirtualFile virtualFile = filePath.getVirtualFile();
      return root.equals(virtualFile) || FileUtil.isAncestor(VfsUtilCore.virtualToIoFile(root), filePath.getIOFile(), false);
    }));
  }

  @Nullable
  public static String getSingleFilteredBranch(@NotNull VcsLogFilterCollection filters, @NotNull VcsLogRefs refs) {
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

  public static boolean maybeRegexp(@NotNull String text) {
    return StringUtil.containsAnyChar(text, "()[]{}.*?+^$\\|");
  }

  @NotNull
  public static TextRevisionNumber convertToRevisionNumber(@NotNull Hash hash) {
    return new TextRevisionNumber(hash.asString(), hash.toShortString());
  }

  @NotNull
  public static VcsFullCommitDetails getDetails(@NotNull VcsLogData data, @NotNull VirtualFile root, @NotNull Hash hash)
    throws VcsException {
    return Objects.requireNonNull(getFirstItem(getDetails(data.getLogProvider(root), root, singletonList(hash.asString()))));
  }

  @NotNull
  public static List<? extends VcsFullCommitDetails> getDetails(@NotNull VcsLogProvider logProvider,
                                                                @NotNull VirtualFile root,
                                                                @NotNull List<String> hashes) throws VcsException {
    List<VcsFullCommitDetails> result = new ArrayList<>();
    logProvider.readFullDetails(root, hashes, result::add);
    return result;
  }

  @NotNull
  public static CommittedChangeListForRevision createCommittedChangeList(@NotNull VcsFullCommitDetails detail, boolean withChanges) {
    return new CommittedChangeListForRevision(detail.getSubject(), detail.getFullMessage(),
                                              VcsUserUtil.getShortPresentation(detail.getCommitter()),
                                              new Date(detail.getCommitTime()),
                                              withChanges ? detail.getChanges() : ContainerUtil.emptyList(),
                                              convertToRevisionNumber(detail.getId()));
  }

  @NotNull
  public static CommittedChangeListForRevision createCommittedChangeList(@NotNull VcsFullCommitDetails detail) {
    return createCommittedChangeList(detail, true);
  }

  @NotNull
  public static String getShortHash(@NotNull String hashString) {
    return getShortHash(hashString, SHORT_HASH_LENGTH);
  }

  @NotNull
  public static String getShortHash(@NotNull String hashString, int shortHashLength) {
    return hashString.substring(0, Math.min(shortHashLength, hashString.length()));
  }

  @Nullable
  public static VcsRef findBranch(@NotNull RefsModel refs, @NotNull VirtualFile root, @NotNull String branchName) {
    CompressedRefs compressedRefs = refs.getAllRefsByRoot().get(root);
    if (compressedRefs == null) return null;
    Stream<VcsRef> branches = compressedRefs.streamBranches();
    return branches.filter(vcsRef -> vcsRef.getName().equals(branchName)).findFirst().orElse(null);
  }

  @NotNull
  public static List<Change> collectChanges(@NotNull List<? extends VcsFullCommitDetails> detailsList,
                                            @NotNull Function<? super VcsFullCommitDetails, ? extends Collection<Change>> getChanges) {
    List<Change> changes = new ArrayList<>();
    List<VcsFullCommitDetails> detailsListReversed = ContainerUtil.reverse(detailsList);
    for (VcsFullCommitDetails details : detailsListReversed) {
      changes.addAll(getChanges.fun(details));
    }

    return CommittedChangesTreeBrowser.zipChanges(changes);
  }

  @Nullable
  public static VirtualFile getActualRoot(@NotNull Project project, @NotNull FilePath path) {
    VcsRoot rootObject = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(path);
    if (rootObject == null) return null;
    Map<VirtualFile, VcsLogProvider> providers = findLogProviders(singletonList(rootObject), project);
    if (providers.isEmpty()) return null;
    VcsLogProvider provider = Objects.requireNonNull(getFirstItem(providers.values()));
    return provider.getVcsRoot(project, rootObject.getPath(), path);
  }

  @Nullable
  public static Collection<FilePath> getAffectedPaths(@NotNull VcsLogUi logUi) {
    return getAffectedPaths(logUi.getDataPack());
  }

  @Nullable
  public static Collection<FilePath> getAffectedPaths(@NotNull VcsLogDataPack dataPack) {
    VcsLogStructureFilter structureFilter = dataPack.getFilters().get(VcsLogFilterCollection.STRUCTURE_FILTER);
    if (structureFilter != null) {
      return structureFilter.getFiles();
    }
    return null;
  }

  @Nullable
  public static Collection<FilePath> getAffectedPaths(@NotNull VirtualFile root, @NotNull AnActionEvent e) {
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

  @NotNull
  @NonNls
  public static String getSizeText(int maxSize) {
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

  @NotNull
  public static String getProvidersMapText(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
    return "[" + StringUtil.join(providers.keySet(), file -> file.getPresentableUrl(), ", ") + "]"; // NON-NLS
  }

  @NotNull
  public static String getVcsDisplayName(@NotNull Project project, @NotNull Collection<VcsLogProvider> logProviders) {
    Set<AbstractVcs> vcs = ContainerUtil.map2SetNotNull(logProviders,
                                                        provider -> VcsUtil.findVcsByKey(project, provider.getSupportedVcs()));
    if (vcs.size() != 1) return VcsLogBundle.message("vcs");
    return Objects.requireNonNull(getFirstItem(vcs)).getDisplayName();
  }

  @NotNull
  public static String getVcsDisplayName(@NotNull Project project, @NotNull VcsLogManager logManager) {
    return getVcsDisplayName(project, logManager.getDataManager().getLogProviders().values());
  }
}
