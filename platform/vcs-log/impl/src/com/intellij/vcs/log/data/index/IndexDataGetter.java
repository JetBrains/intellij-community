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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.util.io.PersistentMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.ui.filter.VcsLogTextFilterImpl;
import com.intellij.vcs.log.util.TroveUtil;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

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
    FileNamesData result = new MyFileNamesData();

    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    if (myRoots.contains(root)) {
      executeAndCatch(() -> {
        myIndexStorage.paths.iterateCommits(path, (changes, commit) -> executeAndCatch(() -> {
          List<Integer> parents = myIndexStorage.parents.get(commit);
          if (parents == null) {
            throw new CorruptedDataException("No parents for commit " + commit);
          }
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
    catch (IOException | StorageException | CorruptedDataException e) {
      myIndexStorage.markCorrupted();
      myFatalErrorsConsumer.consume(this, e);
    }
    catch (RuntimeException e) {
      processRuntimeException(e);
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

  @NotNull
  private TIntHashSet filterUsers(@NotNull Set<VcsUser> users) {
    try {
      return myIndexStorage.users.getCommitsForUsers(users);
    }
    catch (IOException | StorageException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    catch (RuntimeException e) {
      processRuntimeException(e);
    }
    return new TIntHashSet();
  }

  @NotNull
  private TIntHashSet filterPaths(@NotNull Collection<FilePath> paths) {
    try {
      return myIndexStorage.paths.getCommitsForPaths(paths);
    }
    catch (IOException | StorageException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    catch (RuntimeException e) {
      processRuntimeException(e);
    }
    return new TIntHashSet();
  }

  @NotNull
  private TIntHashSet filterMessages(@NotNull VcsLogTextFilter filter) {
    try {
      if (!filter.isRegex()) {
        TIntHashSet commitsForSearch = myIndexStorage.trigrams.getCommitsForSubstring(filter.getText());
        if (commitsForSearch != null) {
          TIntHashSet result = new TIntHashSet();
          commitsForSearch.forEach(commit -> {
            try {
              String value = myIndexStorage.messages.get(commit);
              if (value != null) {
                if (VcsLogTextFilterImpl.matches(filter, value)) {
                  result.add(commit);
                }
              }
            }
            catch (IOException e) {
              myFatalErrorsConsumer.consume(this, e);
              return false;
            }
            return true;
          });
          return result;
        }
      }
    }
    catch (StorageException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    catch (RuntimeException e) {
      processRuntimeException(e);
    }

    return filter(myIndexStorage.messages, message -> VcsLogTextFilterImpl.matches(filter, message));
  }

  @NotNull
  private <T> TIntHashSet filter(@NotNull PersistentMap<Integer, T> map, @NotNull Condition<T> condition) {
    TIntHashSet result = new TIntHashSet();
    try {
      Processor<Integer> processor = integer -> {
        try {
          T value = map.get(integer);
          if (value != null) {
            if (condition.value(value)) {
              result.add(integer);
            }
          }
        }
        catch (IOException e) {
          myFatalErrorsConsumer.consume(this, e);
          return false;
        }
        return true;
      };
      if (myIndexStorage.messages instanceof PersistentHashMap) {
        ((PersistentHashMap<Integer, T>)myIndexStorage.messages).processKeysWithExistingMapping(processor);
      }
      else {
        myIndexStorage.messages.processKeys(processor);
      }
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }

    return result;
  }

  public boolean canFilter(@NotNull List<VcsLogDetailsFilter> filters) {
    if (filters.isEmpty()) return false;
    for (VcsLogDetailsFilter filter : filters) {
      if (filter instanceof VcsLogTextFilter ||
          filter instanceof VcsLogUserFilter ||
          filter instanceof VcsLogStructureFilter) {
        continue;
      }
      return false;
    }
    return true;
  }

  @NotNull
  public Set<Integer> filter(@NotNull List<VcsLogDetailsFilter> detailsFilters) {
    VcsLogTextFilter textFilter = ContainerUtil.findInstance(detailsFilters, VcsLogTextFilter.class);
    VcsLogUserFilter userFilter = ContainerUtil.findInstance(detailsFilters, VcsLogUserFilter.class);
    VcsLogStructureFilter pathFilter = ContainerUtil.findInstance(detailsFilters, VcsLogStructureFilter.class);

    TIntHashSet filteredByMessage = null;
    if (textFilter != null) {
      filteredByMessage = filterMessages(textFilter);
    }

    TIntHashSet filteredByUser = null;
    if (userFilter != null) {
      Set<VcsUser> users = ContainerUtil.newHashSet();
      for (VirtualFile root : myRoots) {
        users.addAll(userFilter.getUsers(root));
      }

      filteredByUser = filterUsers(users);
    }

    TIntHashSet filteredByPath = null;
    if (pathFilter != null) {
      filteredByPath = filterPaths(pathFilter.getFiles());
    }

    return TroveUtil.intersect(filteredByMessage, filteredByPath, filteredByUser);
  }

  private void processRuntimeException(@NotNull RuntimeException e) {
    if (e instanceof ProcessCanceledException) throw e;
    myIndexStorage.markCorrupted();
    if (e.getCause() instanceof IOException || e.getCause() instanceof StorageException) {
      myFatalErrorsConsumer.consume(this, e);
    }
    else {
      throw new RuntimeException(e);
    }
  }

  private class MyFileNamesData extends FileNamesData {
    protected FilePath getPathById(int pathId) {
      return VcsUtil.getFilePath(myIndexStorage.paths.getPath(pathId));
    }
  }

  public static abstract class FileNamesData {
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

  private static class CorruptedDataException extends RuntimeException {
    public CorruptedDataException(@NotNull String message) {
      super(message);
    }
  }
}
