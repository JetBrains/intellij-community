// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.data.index.VcsLogPathsIndex;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class FileNamesData {
  private static final Logger LOG = Logger.getInstance(FileNamesData.class);
  @NotNull private final TIntObjectHashMap<Map<FilePath, Map<Integer, VcsLogPathsIndex.ChangeData>>> myCommitToPathAndChanges =
    new TIntObjectHashMap<>();
  private boolean myHasRenames = false;

  protected abstract FilePath getPathById(int pathId);

  public boolean hasRenames() {
    return myHasRenames;
  }

  public void add(int commit,
                  @NotNull FilePath path,
                  @NotNull List<VcsLogPathsIndex.ChangeData> changes,
                  @NotNull List<Integer> parents) {
    Map<FilePath, Map<Integer, VcsLogPathsIndex.ChangeData>> pathToChanges = myCommitToPathAndChanges.get(commit);
    if (pathToChanges == null) {
      pathToChanges = ContainerUtil.newHashMap();
      myCommitToPathAndChanges.put(commit, pathToChanges);
    }

    if (!myHasRenames) {
      for (VcsLogPathsIndex.ChangeData data : changes) {
        if (data == null) continue;
        if (data.isRename()) {
          myHasRenames = true;
          break;
        }
      }
    }

    Map<Integer, VcsLogPathsIndex.ChangeData> parentToChangesMap = pathToChanges.get(path);
    if (parentToChangesMap == null) parentToChangesMap = ContainerUtil.newHashMap();
    if (!parents.isEmpty()) {
      LOG.assertTrue(parents.size() == changes.size());
      for (int i = 0; i < changes.size(); i++) {
        VcsLogPathsIndex.ChangeData existing = parentToChangesMap.get(parents.get(i));
        if (existing != null) {
          // since we occasionally reindex commits with different rename limit
          // it can happen that we have several change data for a file in a commit
          // one with rename, other without
          // we want to keep a renamed-one, so throwing the other one out
          if (existing.isRename()) continue;
        }
        parentToChangesMap.put(parents.get(i), changes.get(i));
      }
    }
    else {
      // initial commit
      LOG.assertTrue(changes.size() == 1);
      parentToChangesMap.put(-1, changes.get(0));
    }
    pathToChanges.put(path, parentToChangesMap);
  }

  @Nullable
  public FilePath getPathInParentRevision(int commit, int parent, @NotNull FilePath childPath) {
    Map<FilePath, Map<Integer, VcsLogPathsIndex.ChangeData>> filesToChangesMap = myCommitToPathAndChanges.get(commit);
    LOG.assertTrue(filesToChangesMap != null, "Missing commit " + commit);
    Map<Integer, VcsLogPathsIndex.ChangeData> changes = filesToChangesMap.get(childPath);
    if (changes == null) return childPath;

    VcsLogPathsIndex.ChangeData change = changes.get(parent);
    if (change == null) {
      LOG.assertTrue(changes.size() > 1);
      return childPath;
    }
    if (change.kind.equals(VcsLogPathsIndex.ChangeKind.RENAMED_FROM)) return null;
    if (change.kind.equals(VcsLogPathsIndex.ChangeKind.RENAMED_TO)) {
      return getPathById(change.otherPath);
    }
    return childPath;
  }

  @Nullable
  public FilePath getPathInChildRevision(int commit, int parentIndex, @NotNull FilePath parentPath) {
    Map<FilePath, Map<Integer, VcsLogPathsIndex.ChangeData>> filesToChangesMap = myCommitToPathAndChanges.get(commit);
    LOG.assertTrue(filesToChangesMap != null, "Missing commit " + commit);
    Map<Integer, VcsLogPathsIndex.ChangeData> changes = filesToChangesMap.get(parentPath);
    if (changes == null) return parentPath;

    VcsLogPathsIndex.ChangeData change = changes.get(parentIndex);
    if (change == null) return parentPath;
    if (change.kind.equals(VcsLogPathsIndex.ChangeKind.RENAMED_TO)) return null;
    if (change.kind.equals(VcsLogPathsIndex.ChangeKind.RENAMED_FROM)) {
      return getPathById(change.otherPath);
    }
    return parentPath;
  }

  public boolean affects(int id, @NotNull FilePath path) {
    return myCommitToPathAndChanges.containsKey(id) && myCommitToPathAndChanges.get(id).containsKey(path);
  }

  @NotNull
  public Set<Integer> getCommits() {
    Set<Integer> result = ContainerUtil.newHashSet();
    myCommitToPathAndChanges.forEach(result::add);
    return result;
  }

  @NotNull
  public Map<Integer, FilePath> buildPathsMap() {
    Map<Integer, FilePath> result = ContainerUtil.newHashMap();

    myCommitToPathAndChanges.forEachEntry((commit, filesToChanges) -> {
      if (filesToChanges.size() == 1) {
        result.put(commit, ContainerUtil.getFirstItem(filesToChanges.keySet()));
      }
      else {
        for (Map.Entry<FilePath, Map<Integer, VcsLogPathsIndex.ChangeData>> fileToChange : filesToChanges.entrySet()) {
          VcsLogPathsIndex.ChangeData changeData = ContainerUtil.find(fileToChange.getValue().values(),
                                                                      ch -> ch != null &&
                                                                            !ch.kind.equals(VcsLogPathsIndex.ChangeKind.RENAMED_FROM));
          if (changeData != null) {
            result.put(commit, fileToChange.getKey());
            break;
          }
        }
      }

      return true;
    });

    return result;
  }

  public boolean isTrivialMerge(int commit, @NotNull FilePath path) {
    if (!myCommitToPathAndChanges.containsKey(commit)) return false;
    Map<Integer, VcsLogPathsIndex.ChangeData> data = myCommitToPathAndChanges.get(commit).get(path);
    // strictly speaking, the criteria for merge triviality is a little bit more tricky than this:
    // some merges have just reverted changes in one of the branches
    // they need to be displayed
    // but we skip them instead
    return data != null && data.size() > 1 && data.containsValue(null);
  }
}
