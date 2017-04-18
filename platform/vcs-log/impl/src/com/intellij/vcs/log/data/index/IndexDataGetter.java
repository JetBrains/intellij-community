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

import com.google.common.primitives.Ints;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.UnorderedPair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.indexing.StorageException;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class IndexDataGetter {
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
        myIndexStorage.paths.iterateCommits(Collections.singleton(path), (paths, commit) -> result.add(commit, paths));
        result.pack();
      }
      catch (IOException | StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
    }

    return result;
  }

  public static class FileNamesData {
    @NotNull private final Interner<Set<FilePath>> myPathsInterner = new Interner<>();
    @NotNull private final TIntObjectHashMap<Set<FilePath>> myCommitsToPaths;
    @NotNull private final TIntObjectHashMap<Set<UnorderedPair<FilePath>>> myCommitsToRenames;

    public FileNamesData() {
      myCommitsToPaths = new TIntObjectHashMap<>();
      myCommitsToRenames = new TIntObjectHashMap<>();
    }

    public boolean hasRenames() {
      return !myCommitsToRenames.isEmpty();
    }

    private void addPath(int commit, @NotNull FilePath path) {
      Set<FilePath> paths = myCommitsToPaths.get(commit);
      if (paths == null) {
        paths = new SmartHashSet<>();
        myCommitsToPaths.put(commit, paths);
      }
      paths.add(path);
    }

    private void addRename(int commit, @NotNull Couple<FilePath> path) {
      Set<UnorderedPair<FilePath>> paths = myCommitsToRenames.get(commit);
      if (paths == null) {
        paths = ContainerUtil.newHashSet();
        myCommitsToRenames.put(commit, paths);
      }
      paths.add(new UnorderedPair<>(path.first, path.second));
    }

    private void add(int commit, @NotNull Couple<FilePath> paths) {
      if (paths.second == null) {
        addPath(commit, paths.first);
      }
      else {
        addRename(commit, paths);
      }
    }

    public boolean affects(int commit, @NotNull FilePath path) {
      Set<FilePath> paths = myCommitsToPaths.get(commit);
      if (paths != null && paths.contains(path)) return true;
      return getRenamedPath(commit, path) != null;
    }

    @Nullable
    public FilePath getRenamedPath(int commit, @Nullable FilePath newName) {
      Set<UnorderedPair<FilePath>> renames = myCommitsToRenames.get(commit);
      if (renames == null) return null;

      for (UnorderedPair<FilePath> rename : renames) {
        if (rename.first.equals(newName)) return rename.second;
        if (rename.second.equals(newName)) return rename.first;
      }
      return null;
    }

    @Nullable
    public FilePath getPreviousPath(int commit, @Nullable FilePath path) {
      Set<FilePath> paths = myCommitsToPaths.get(commit);
      if (paths != null && paths.contains(path)) return path;
      return getRenamedPath(commit, path);
    }

    public void remove(int commit) {
      myCommitsToPaths.remove(commit);
      myCommitsToRenames.remove(commit);
    }

    public void retain(int commit, @NotNull FilePath path, @NotNull FilePath previousPath) {
      if (path.equals(previousPath)) {
        myCommitsToPaths.put(commit, myPathsInterner.intern(ContainerUtil.set(path)));
        myCommitsToRenames.remove(commit);
      }
      else {
        myCommitsToPaths.remove(commit);
        myCommitsToRenames.put(commit, ContainerUtil.set(new UnorderedPair<>(path, previousPath)));
      }
    }

    @NotNull
    public Set<FilePath> getAffectedPaths(int commit) {
      Set<FilePath> result = new SmartHashSet<>();

      Set<FilePath> paths = myCommitsToPaths.get(commit);
      if (paths != null) result.addAll(paths);

      Set<UnorderedPair<FilePath>> renames = myCommitsToRenames.get(commit);
      if (renames != null) {
        for (UnorderedPair<FilePath> rename : renames) {
          result.add(rename.first);
          result.add(rename.second);
        }
      }

      return result;
    }

    void pack() {
      TIntObjectIterator<Set<FilePath>> iterator = myCommitsToPaths.iterator();
      while (iterator.hasNext()) {
        iterator.advance();
        iterator.setValue(myPathsInterner.intern(iterator.value()));
      }
    }

    @NotNull
    public Set<Integer> getCommits() {
      return ContainerUtil.union(Ints.asList(myCommitsToPaths.keys()), Ints.asList(myCommitsToRenames.keys()));
    }
  }
}
