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
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndexDataGetter {
  private static final Logger LOG = Logger.getInstance(IndexDataGetter.class);

  @NotNull private final Project myProject;
  @NotNull private final Set<VirtualFile> myRoots;
  @NotNull private final VcsLogPersistentIndex.IndexStorage myIndexStorage;
  @NotNull private final VcsLogStorage myLogStorage;
  @NotNull private final FatalErrorHandler myFatalErrorsConsumer;

  public IndexDataGetter(@NotNull Project project,
                         @NotNull Set<VirtualFile> roots,
                         @NotNull VcsLogPersistentIndex.IndexStorage indexStorage,
                         @NotNull VcsLogStorage logStorage,
                         @NotNull FatalErrorHandler fatalErrorsConsumer) {
    myProject = project;
    myRoots = roots;
    myIndexStorage = indexStorage;
    myLogStorage = logStorage;
    myFatalErrorsConsumer = fatalErrorsConsumer;
  }

  @Nullable
  public String getFullMessage(int index) {
    return executeAndCatch(() -> myIndexStorage.messages.get(index));
  }

  @NotNull
  public Set<FilePath> getFileNames(@NotNull FilePath path, int commit) {
    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    if (myRoots.contains(root)) {
      Set<FilePath> result = executeAndCatch(() -> myIndexStorage.paths.getFileNames(path, commit));
      if (result != null) return result;
    }

    return Collections.emptySet();
  }

  @NotNull
  public FileNamesData buildFileNamesData(@NotNull FilePath path) {
    FileNamesData result = new FileNamesData();

    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    if (myRoots.contains(root)) {
      executeAndCatch(() -> {
        myIndexStorage.paths.iterateCommits(path, (changes, commit) -> executeAndCatch(() -> {
          List<Integer> parents = myIndexStorage.parents.get(commit);
          result.add(commit, changes.first, changes.second, parents);
          return null;
        }));
        return null;
      });
    }

    return result;
  }

  @Nullable
  private <T> T executeAndCatch(@NotNull Throwable2Computable<T, IOException, StorageException> computable) {
    try {
      return computable.compute();
    }
    catch (IOException | StorageException e) {
      myIndexStorage.markCorrupted();
      myFatalErrorsConsumer.consume(this, e);
    }
    catch (RuntimeException e) {
      if (e.getCause() instanceof IOException || e.getCause() instanceof StorageException) {
        myIndexStorage.markCorrupted();
        myFatalErrorsConsumer.consume(this, e);
      }
      else {
        throw e;
      }
    }
    return null;
  }

  @NotNull
  public List<Hash> getParents(int index) {
    try {
      List<Integer> parentsIndexes = myIndexStorage.parents.get(index);
      if (parentsIndexes == null) return Collections.emptyList();
      List<Hash> result = ContainerUtil.newArrayList();
      for (int parentIndex : parentsIndexes) {
        CommitId id = myLogStorage.getCommitId(parentIndex);
        if (id == null) return Collections.emptyList();
        result.add(id.getHash());
      }
      return result;
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    return Collections.emptyList();
  }

  public class FileNamesData {
    @NotNull private final TIntObjectHashMap<Map<FilePath, Map<Integer, VcsLogPathsIndex.ChangeData>>> myCommitToPathAndChanges =
      new TIntObjectHashMap<>();
    private boolean myHasRenames = false;

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
          if (data.kind.equals(VcsLogPathsIndex.ChangeKind.RENAMED_FROM) || data.kind.equals(VcsLogPathsIndex.ChangeKind.RENAMED_TO)) {
            myHasRenames = true;
            break;
          }
        }
      }

      Map<Integer, VcsLogPathsIndex.ChangeData> parentToChangesMap = ContainerUtil.newHashMap();
      if (!parents.isEmpty()) {
        LOG.assertTrue(parents.size() == changes.size());
        for (int i = 0; i < changes.size(); i++) {
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
        return VcsUtil.getFilePath(myIndexStorage.paths.getPath(change.otherPath));
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
        return VcsUtil.getFilePath(myIndexStorage.paths.getPath(change.otherPath));
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
}
