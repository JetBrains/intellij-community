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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
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
  @NotNull private final FatalErrorHandler myFatalErrorsConsumer;

  public IndexDataGetter(@NotNull Project project,
                         @NotNull Set<VirtualFile> roots,
                         @NotNull VcsLogPersistentIndex.IndexStorage storage,
                         @NotNull FatalErrorHandler fatalErrorsConsumer) {
    myProject = project;
    myRoots = roots;
    myIndexStorage = storage;
    myFatalErrorsConsumer = fatalErrorsConsumer;
  }

  @Nullable
  public String getFullMessage(int index) {
    try {
      return myIndexStorage.messages.get(index);
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    return null;
  }

  @NotNull
  public Set<FilePath> getFileNames(@NotNull FilePath path, int commit) {
    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    if (myRoots.contains(root)) {
      try {
        return myIndexStorage.paths.getFileNames(path, commit);
      }
      catch (IOException | StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
    }

    return Collections.emptySet();
  }

  @NotNull
  public FileNamesData buildFileNamesData(@NotNull FilePath path) {
    FileNamesData result = new FileNamesData();

    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    if (myRoots.contains(root)) {
      try {
        myIndexStorage.paths.iterateCommits(path, (changes, commit) -> result.add(commit, changes.first, changes.second));
      }
      catch (IOException | StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
    }

    return result;
  }

  public class FileNamesData {
    @NotNull private final TIntObjectHashMap<Map<FilePath, List<VcsLogPathsIndex.ChangeData>>> myCommitToChanges =
      new TIntObjectHashMap<>();
    private boolean myHasRenames = false;

    public boolean hasRenames() {
      return myHasRenames;
    }

    public void add(int commit, @NotNull FilePath path, @NotNull List<VcsLogPathsIndex.ChangeData> changes) {
      Map<FilePath, List<VcsLogPathsIndex.ChangeData>> map = myCommitToChanges.get(commit);
      if (map == null) {
        map = ContainerUtil.newHashMap();
        myCommitToChanges.put(commit, map);
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
      map.put(path, changes);
    }

    @Nullable
    public FilePath getPathInParentRevision(int commit, int parentIndex, @NotNull FilePath childPath) {
      Map<FilePath, List<VcsLogPathsIndex.ChangeData>> filesToChangesMap = myCommitToChanges.get(commit);
      LOG.assertTrue(filesToChangesMap != null);
      List<VcsLogPathsIndex.ChangeData> changes = filesToChangesMap.get(childPath);
      if (changes == null) return childPath;

      VcsLogPathsIndex.ChangeData change = changes.get(parentIndex);
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
      Map<FilePath, List<VcsLogPathsIndex.ChangeData>> filesToChangesMap = myCommitToChanges.get(commit);
      LOG.assertTrue(filesToChangesMap != null);
      List<VcsLogPathsIndex.ChangeData> changes = filesToChangesMap.get(parentPath);
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
      return myCommitToChanges.containsKey(id) && myCommitToChanges.get(id).containsKey(path);
    }

    @NotNull
    public Set<Integer> getCommits() {
      Set<Integer> result = ContainerUtil.newHashSet();
      myCommitToChanges.forEach(result::add);
      return result;
    }

    @NotNull
    public Map<Integer, FilePath> buildPathsMap() {
      Map<Integer, FilePath> result = ContainerUtil.newHashMap();

      myCommitToChanges.forEachEntry((commit, filesToChanges) -> {
        if (filesToChanges.size() == 1) {
          result.put(commit, ContainerUtil.getFirstItem(filesToChanges.keySet()));
        }
        else {
          for (Map.Entry<FilePath, List<VcsLogPathsIndex.ChangeData>> fileToChange : filesToChanges.entrySet()) {
            VcsLogPathsIndex.ChangeData changeData =
              ContainerUtil.find(fileToChange.getValue(), ch -> ch != null && !ch.kind.equals(VcsLogPathsIndex.ChangeKind.RENAMED_FROM));
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
      if (!myCommitToChanges.containsKey(commit)) return false;
      List<VcsLogPathsIndex.ChangeData> data = myCommitToChanges.get(commit).get(path);
      // strictly speaking, the criteria for merge triviality is a little bit more tricky than this:
      // some merges have just reverted changes in one of the branches
      // they need to be displayed
      // but we skip them instead
      return data != null && data.size() > 1 && data.contains(null);
    }
  }
}
