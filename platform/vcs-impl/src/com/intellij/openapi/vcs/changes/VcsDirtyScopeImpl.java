// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class VcsDirtyScopeImpl extends VcsModifiableDirtyScope implements VcsDirtyScopeBuilder {
  private final Map<VirtualFile, Set<FilePath>> myDirtyFiles = new HashMap<>();
  private final Map<VirtualFile, RecursiveFilePathSet> myDirtyDirectoriesRecursively = new HashMap<>();
  private final Set<FilePath> myAllVcsRoots = new HashSet<>();
  private final Set<VirtualFile> myAffectedVcsRoots = new HashSet<>();
  private final @NotNull Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final @NotNull AbstractVcs myVcs;
  private boolean myWasEverythingDirty;

  private final @Nullable HashingStrategy<FilePath> myHashingStrategy;
  private final boolean myCaseSensitive;

  public VcsDirtyScopeImpl(@NotNull AbstractVcs vcs) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myHashingStrategy = VcsDirtyScopeManagerImpl.getDirtyScopeHashingStrategy(myVcs);
    myCaseSensitive = myVcs.needsCaseSensitiveDirtyScope() || SystemInfo.isFileSystemCaseSensitive;
    myAllVcsRoots.addAll(ContainerUtil.map(myVcsManager.getRootsUnderVcsWithoutFiltering(myVcs), root -> VcsUtil.getFilePath(root)));
  }

  @Override
  public Collection<VirtualFile> getAffectedContentRoots() {
    return myAffectedVcsRoots;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  public Set<FilePath> getDirtyFiles() {
    Set<FilePath> result = newFilePathsSet();
    for (Set<FilePath> paths : myDirtyFiles.values()) {
      result.addAll(paths);
    }
    for (Set<FilePath> paths : myDirtyFiles.values()) {
      for (FilePath filePath : paths) {
        VirtualFile vFile = filePath.getVirtualFile();
        if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
          for (VirtualFile child : vFile.getChildren()) {
            result.add(VcsUtil.getFilePath(child));
          }
        }
      }
    }
    return result;
  }

  @Override
  public Set<FilePath> getDirtyFilesNoExpand() {
    final Set<FilePath> paths = newFilePathsSet();
    for (Set<FilePath> filePaths : myDirtyFiles.values()) {
      paths.addAll(filePaths);
    }
    return paths;
  }

  @Override
  public Set<FilePath> getRecursivelyDirtyDirectories() {
    Set<FilePath> result = newFilePathsSet();
    for (RecursiveFilePathSet dirsByRoot : myDirtyDirectoriesRecursively.values()) {
      result.addAll(dirsByRoot.filePaths());
    }
    return result;
  }

  /**
   * Add file path into the sets, without removing potential duplicates.
   * See {@link #pack()}, that will be called later to perform this optimization.
   * <p>
   * Use {@link #addDirtyFile} / {@link #addDirtyDirRecursively} to add file path and remove duplicates.
   */
  @Override
  public void addDirtyPathFast(@NotNull VirtualFile vcsRoot, @NotNull FilePath filePath, boolean recursively) {
    myAffectedVcsRoots.add(vcsRoot);

    RecursiveFilePathSet dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
    if (dirsByRoot != null && dirsByRoot.hasAncestor(filePath)) return;

    if (recursively) {
      if (dirsByRoot == null) {
        dirsByRoot = newRecursiveFilePathSet();
        myDirtyDirectoriesRecursively.put(vcsRoot, dirsByRoot);
      }
      dirsByRoot.add(filePath);
    }
    else {
      Set<FilePath> dirtyFiles = myDirtyFiles.get(vcsRoot);
      if (dirtyFiles == null) {
        dirtyFiles = newFilePathsSet();
        myDirtyFiles.put(vcsRoot, dirtyFiles);
      }
      dirtyFiles.add(filePath);
    }
  }

  @Override
  public void markEverythingDirty() {
    myWasEverythingDirty = true;
  }

  /**
   * @return VcsDirtyScope with trimmed duplicated paths from the sets.
   */
  @Override
  public @NotNull VcsDirtyScopeImpl pack() {
    VcsDirtyScopeImpl copy = new VcsDirtyScopeImpl(myVcs);
    copy.myWasEverythingDirty = myWasEverythingDirty;
    for (VirtualFile root : myAffectedVcsRoots) {
      RecursiveFilePathSet rootDirs = myDirtyDirectoriesRecursively.get(root);
      Set<FilePath> rootFiles = ContainerUtil.notNullize(myDirtyFiles.get(root));

      RecursiveFilePathSet filteredDirs = removeAncestorsRecursive(rootDirs);
      Set<FilePath> filteredFiles = removeAncestorsNonRecursive(filteredDirs, rootFiles);

      copy.myAffectedVcsRoots.add(root);
      copy.myDirtyDirectoriesRecursively.put(root, filteredDirs);
      copy.myDirtyFiles.put(root, filteredFiles);
    }
    return copy;
  }

  private @NotNull RecursiveFilePathSet removeAncestorsRecursive(@Nullable RecursiveFilePathSet dirs) {
    RecursiveFilePathSet result = newRecursiveFilePathSet();
    if (dirs == null) return result;

    List<FilePath> paths = ContainerUtil.sorted(dirs.filePaths(), Comparator.comparingInt(it -> it.getPath().length()));
    for (FilePath path : paths) {
      if (result.hasAncestor(path)) continue;
      result.add(path);
    }
    return result;
  }

  private @NotNull Set<FilePath> removeAncestorsNonRecursive(@NotNull RecursiveFilePathSet dirs,
                                                             @NotNull Set<? extends FilePath> files) {
    Set<FilePath> result = newFilePathsSet();
    for (FilePath file : files) {
      if (dirs.hasAncestor(file)) continue;
      // if dir non-recursively + non-recursive file child -> can be truncated to dir only
      if (!file.isDirectory() && files.contains(file.getParentPath())) continue;
      result.add(file);
    }
    return result;
  }

  private @NotNull Set<FilePath> newFilePathsSet() {
    return myHashingStrategy == null ? new HashSet<>() : CollectionFactory.createCustomHashingStrategySet(myHashingStrategy);
  }

  private @NotNull RecursiveFilePathSet newRecursiveFilePathSet() {
    return new RecursiveFilePathSet(myCaseSensitive);
  }

  /**
   * Add dirty directory recursively. If there are already dirty entries
   * that are descendants or ancestors for the added directory, the contained
   * entries are dropped from scope.
   *
   * @param newcomer a new directory to add
   */
  @Override
  public void addDirtyDirRecursively(final FilePath newcomer) {
    final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(newcomer);
    if (vcsRoot == null) return;
    myAffectedVcsRoots.add(vcsRoot);

    for (Map.Entry<VirtualFile, Set<FilePath>> entry : myDirtyFiles.entrySet()) {
      final VirtualFile groupRoot = entry.getKey();
      if (groupRoot != null && VfsUtilCore.isAncestor(vcsRoot, groupRoot, false)) {
        Set<FilePath> files = entry.getValue();
        if (files != null) {
          for (Iterator<FilePath> it = files.iterator(); it.hasNext(); ) {
            FilePath oldBoy = it.next();
            if (VcsFileUtil.isAncestor(newcomer, oldBoy, false)) {
              it.remove();
            }
          }
        }
      }
    }

    RecursiveFilePathSet dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
    if (dirsByRoot == null) {
      dirsByRoot = newRecursiveFilePathSet();
      myDirtyDirectoriesRecursively.put(vcsRoot, dirsByRoot);
    }
    else {
      if (dirsByRoot.hasAncestor(newcomer)) return;

      List<FilePath> toRemove = ContainerUtil.filter(dirsByRoot.filePaths(),
                                                     oldBoy -> VcsFileUtil.isAncestor(newcomer, oldBoy, false));
      for (FilePath path : toRemove) {
        dirsByRoot.remove(path);
      }
    }

    dirsByRoot.add(newcomer);
  }

  /**
   * Add dirty file to the scope. Note that file is not added if its ancestor was added as dirty recursively or if its parent is in already
   * in the dirty scope. Also immediate non-directory children are removed from the set of dirty files.
   *
   * @param newcomer a file or directory added to the dirty scope.
   */
  @Override
  public void addDirtyFile(final FilePath newcomer) {
    final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(newcomer);
    if (vcsRoot == null) return;
    myAffectedVcsRoots.add(vcsRoot);

    RecursiveFilePathSet dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
    if (dirsByRoot != null && dirsByRoot.hasAncestor(newcomer)) {
      return;
    }

    Set<FilePath> dirtyFiles = myDirtyFiles.get(vcsRoot);
    if (dirtyFiles == null) {
      dirtyFiles = newFilePathsSet();
      myDirtyFiles.put(vcsRoot, dirtyFiles);
    }
    else {
      if (newcomer.isDirectory()) {
        for (Iterator<FilePath> iterator = dirtyFiles.iterator(); iterator.hasNext(); ) {
          final FilePath oldBoy = iterator.next();
          if (!oldBoy.isDirectory() &&
              (myHashingStrategy == null
               ? Objects.equals(oldBoy.getParentPath(), newcomer)
               : myHashingStrategy.equals(oldBoy.getParentPath(), newcomer))) {
            iterator.remove();
          }
        }
      }
      else if (!dirtyFiles.isEmpty()) {
        FilePath parent = newcomer.getParentPath();
        if (parent != null && dirtyFiles.contains(parent)) {
          return;
        }
      }
    }

    dirtyFiles.add(newcomer);
  }

  @Override
  public boolean isEmpty() {
    return myDirtyDirectoriesRecursively.isEmpty() && myDirtyFiles.isEmpty();
  }

  @Override
  public boolean belongsTo(final FilePath path) {
    if (myProject.isDisposed()) return false;
    final VcsRoot rootObject = myVcsManager.getVcsRootObjectFor(path);
    if (rootObject == null || rootObject.getVcs() != myVcs) {
      return false;
    }

    final VirtualFile vcsRoot = rootObject.getPath();
    boolean pathIsRoot = myAllVcsRoots.contains(path);
    for (VirtualFile otherRoot : myDirtyDirectoriesRecursively.keySet()) {
      // since we don't know exact dirty mechanics, maybe we have 3 nested mappings like:
      // /root -> vcs1, /root/child -> vcs2, /root/child/inner -> vcs1, and we have file /root/child/inner/file,
      // mapping is detected as vcs1 with root /root/child/inner, but we could possibly have in scope
      // "affected root" -> /root with scope = /root recursively
      boolean strict = pathIsRoot && !myVcs.areDirectoriesVersionedItems();
      if (VfsUtilCore.isAncestor(otherRoot, vcsRoot, strict)) {
        RecursiveFilePathSet dirsByRoot = myDirtyDirectoriesRecursively.get(otherRoot);
        if (dirsByRoot.hasAncestor(path)) {
          return true;
        }
      }
    }

    if (!myDirtyFiles.isEmpty()) {
      if (isInDirtyFiles(path, vcsRoot)) return true;

      FilePath parent = path.getParentPath();
      if (parent != null) {
        if (pathIsRoot) {
          if (isInDirtyFiles(parent)) return true;
        }
        else {
          if (isInDirtyFiles(parent, vcsRoot)) return true;
        }
      }
    }

    return false;
  }

  private boolean isInDirtyFiles(@NotNull FilePath path) {
    VcsRoot rootObject = myVcsManager.getVcsRootObjectFor(path);
    if (rootObject == null || !myVcs.equals(rootObject.getVcs())) return false;
    return isInDirtyFiles(path, rootObject.getPath());
  }

  private boolean isInDirtyFiles(@NotNull FilePath path, @NotNull VirtualFile vcsRoot) {
    final Set<FilePath> files = myDirtyFiles.get(vcsRoot);
    return files != null && files.contains(path);
  }

  @Override
  public @NonNls String toString() {
    @NonNls StringBuilder result = new StringBuilder("VcsDirtyScope[");
    if (!myDirtyFiles.isEmpty()) {
      result.append(" files: ");
      for (Set<FilePath> paths : myDirtyFiles.values()) {
        for (FilePath file : paths) {
          result.append(file).append(" ");
        }
      }
    }
    if (!myDirtyDirectoriesRecursively.isEmpty()) {
      result.append("\ndirs: ");
      for (RecursiveFilePathSet dirsByRoot : myDirtyDirectoriesRecursively.values()) {
        for (FilePath file : dirsByRoot.filePaths()) {
          result.append(file).append(" ");
        }
      }
    }
    result.append("\naffected roots: ");
    for (VirtualFile root : myAffectedVcsRoots) {
      result.append(root.getPath()).append(" ");
    }
    result.append("]");
    return result.toString();
  }

  @Override
  public boolean wasEveryThingDirty() {
    return myWasEverythingDirty;
  }
}
