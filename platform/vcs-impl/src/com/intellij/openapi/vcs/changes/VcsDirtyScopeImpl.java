// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.impl.projectlevelman.RecursiveFilePathSet;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl.getDirtyScopeHashingStrategy;
import static com.intellij.util.containers.ContainerUtil.notNullize;

/**
 * @author max
 * @author yole
 */
public class VcsDirtyScopeImpl extends VcsModifiableDirtyScope {
  private final Map<VirtualFile, THashSet<FilePath>> myDirtyFiles = new HashMap<>();
  private final Map<VirtualFile, RecursiveFilePathSet> myDirtyDirectoriesRecursively = new HashMap<>();
  private final Set<VirtualFile> myAffectedContentRoots = new THashSet<>();
  @NotNull private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final AbstractVcs myVcs;
  private final boolean myWasEverythingDirty;

  @NotNull private final TObjectHashingStrategy<FilePath> myHashingStrategy;
  private final boolean myCaseSensitive;

  public VcsDirtyScopeImpl(@NotNull AbstractVcs vcs) {
    this(vcs, false);
  }

  public VcsDirtyScopeImpl(@NotNull AbstractVcs vcs, boolean wasEverythingDirty) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myWasEverythingDirty = wasEverythingDirty;
    myHashingStrategy = getDirtyScopeHashingStrategy(myVcs);
    myCaseSensitive = myVcs.needsCaseSensitiveDirtyScope() || SystemInfo.isFileSystemCaseSensitive;
  }

  @Override
  public Collection<VirtualFile> getAffectedContentRoots() {
    return myAffectedContentRoots;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  public Set<FilePath> getDirtyFiles() {
    final THashSet<FilePath> result = newFilePathsSet();
    for (THashSet<FilePath> paths : myDirtyFiles.values()) {
      result.addAll(paths);
    }
    for (THashSet<FilePath> paths : myDirtyFiles.values()) {
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
    final THashSet<FilePath> paths = newFilePathsSet();
    for (THashSet<FilePath> filePaths : myDirtyFiles.values()) {
      paths.addAll(filePaths);
    }
    return paths;
  }

  @Override
  public Set<FilePath> getRecursivelyDirtyDirectories() {
    THashSet<FilePath> result = newFilePathsSet();
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
  public void addDirtyPathFast(@NotNull VirtualFile vcsRoot, @NotNull FilePath filePath, boolean recursively) {
    myAffectedContentRoots.add(vcsRoot);

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
      THashSet<FilePath> dirtyFiles = myDirtyFiles.get(vcsRoot);
      if (dirtyFiles == null) {
        dirtyFiles = newFilePathsSet();
        myDirtyFiles.put(vcsRoot, dirtyFiles);
      }
      dirtyFiles.add(filePath);
    }
  }

  /**
   * @return VcsDirtyScope with trimmed duplicated paths from the sets.
   */
  @NotNull
  public VcsDirtyScopeImpl pack() {
    VcsDirtyScopeImpl copy = new VcsDirtyScopeImpl(myVcs, myWasEverythingDirty);
    for (VirtualFile root : myAffectedContentRoots) {
      RecursiveFilePathSet rootDirs = myDirtyDirectoriesRecursively.get(root);
      Set<FilePath> rootFiles = notNullize(myDirtyFiles.get(root));

      RecursiveFilePathSet filteredDirs = removeAncestorsRecursive(rootDirs);
      THashSet<FilePath> filteredFiles = removeAncestorsNonRecursive(filteredDirs, rootFiles);

      copy.myAffectedContentRoots.add(root);
      copy.myDirtyDirectoriesRecursively.put(root, filteredDirs);
      copy.myDirtyFiles.put(root, filteredFiles);
    }
    return copy;
  }

  @NotNull
  private RecursiveFilePathSet removeAncestorsRecursive(@Nullable RecursiveFilePathSet dirs) {
    RecursiveFilePathSet result = newRecursiveFilePathSet();
    if (dirs == null) return result;

    List<FilePath> paths = ContainerUtil.sorted(dirs.filePaths(), Comparator.comparingInt(it -> it.getPath().length()));
    for (FilePath path : paths) {
      if (result.hasAncestor(path)) continue;
      result.add(path);
    }
    return result;
  }

  @NotNull
  private THashSet<FilePath> removeAncestorsNonRecursive(@NotNull RecursiveFilePathSet dirs,
                                                         @NotNull Set<? extends FilePath> files) {
    THashSet<FilePath> result = newFilePathsSet();
    for (FilePath file : files) {
      if (dirs.hasAncestor(file)) continue;
      // if dir non-recursively + non-recursive file child -> can be truncated to dir only
      if (!file.isDirectory() && files.contains(file.getParentPath())) continue;
      result.add(file);
    }
    return result;
  }

  @NotNull
  private THashSet<FilePath> newFilePathsSet() {
    return new THashSet<>(myHashingStrategy);
  }

  @NotNull
  private RecursiveFilePathSet newRecursiveFilePathSet() {
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
    myAffectedContentRoots.add(vcsRoot);

    for (Map.Entry<VirtualFile, THashSet<FilePath>> entry : myDirtyFiles.entrySet()) {
      final VirtualFile groupRoot = entry.getKey();
      if (groupRoot != null && VfsUtilCore.isAncestor(vcsRoot, groupRoot, false)) {
        final THashSet<FilePath> files = entry.getValue();
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
    myAffectedContentRoots.add(vcsRoot);

    RecursiveFilePathSet dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
    if (dirsByRoot != null && dirsByRoot.hasAncestor(newcomer)) {
      return;
    }

    THashSet<FilePath> dirtyFiles = myDirtyFiles.get(vcsRoot);
    if (dirtyFiles == null) {
      dirtyFiles = newFilePathsSet();
      myDirtyFiles.put(vcsRoot, dirtyFiles);
    }
    else {
      if (newcomer.isDirectory()) {
        for (Iterator<FilePath> iterator = dirtyFiles.iterator(); iterator.hasNext(); ) {
          final FilePath oldBoy = iterator.next();
          if (!oldBoy.isDirectory() && myHashingStrategy.equals(oldBoy.getParentPath(), newcomer)) {
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
  public void iterate(final Processor<? super FilePath> iterator) {
    if (myProject.isDisposed()) return;

    for (VirtualFile root : myAffectedContentRoots) {
      RecursiveFilePathSet dirsByRoot = myDirtyDirectoriesRecursively.get(root);
      if (dirsByRoot != null) {
        for (FilePath dir : dirsByRoot.filePaths()) {
          final VirtualFile vFile = dir.getVirtualFile();
          if (vFile != null && vFile.isValid()) {
            myVcsManager.iterateVcsRoot(vFile, iterator);
          }
        }
      }
    }

    for (VirtualFile root : myAffectedContentRoots) {
      final THashSet<FilePath> files = myDirtyFiles.get(root);
      if (files != null) {
        for (FilePath file : files) {
          iterator.process(file);
          final VirtualFile vFile = file.getVirtualFile();
          if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
            for (VirtualFile child : vFile.getChildren()) {
              iterator.process(VcsUtil.getFilePath(child));
            }
          }
        }
      }
    }
  }

  @Override
  public void iterateExistingInsideScope(Processor<? super VirtualFile> processor) {
    if (myProject.isDisposed()) return;

    for (VirtualFile root : myAffectedContentRoots) {
      RecursiveFilePathSet dirsByRoot = myDirtyDirectoriesRecursively.get(root);
      if (dirsByRoot != null) {
        for (FilePath dir : dirsByRoot.filePaths()) {
          final VirtualFile vFile = obtainVirtualFile(dir);
          if (vFile != null && vFile.isValid()) {
            myVcsManager.iterateVfUnderVcsRoot(vFile, processor);
          }
        }
      }
    }

    for (VirtualFile root : myAffectedContentRoots) {
      final THashSet<FilePath> files = myDirtyFiles.get(root);
      if (files != null) {
        for (FilePath file : files) {
          VirtualFile vFile = obtainVirtualFile(file);
          if (vFile != null && vFile.isValid()) {
            processor.process(vFile);
            if (vFile.isDirectory()) {
              for (VirtualFile child : vFile.getChildren()) {
                processor.process(child);
              }
            }
          }
        }
      }
    }
  }

  @Nullable
  private static VirtualFile obtainVirtualFile(FilePath file) {
    VirtualFile vFile = file.getVirtualFile();
    return vFile == null ? VfsUtil.findFileByIoFile(file.getIOFile(), false) : vFile;
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
    boolean pathIsRoot = vcsRoot.equals(path.getVirtualFile());
    for (VirtualFile contentRoot : myDirtyDirectoriesRecursively.keySet()) {
      // since we don't know exact dirty mechanics, maybe we have 3 nested mappings like:
      // /root -> vcs1, /root/child -> vcs2, /root/child/inner -> vcs1, and we have file /root/child/inner/file,
      // mapping is detected as vcs1 with root /root/child/inner, but we could possibly have in scope
      // "affected root" -> /root with scope = /root recursively
      boolean strict = pathIsRoot && !myVcs.areDirectoriesVersionedItems();
      if (VfsUtilCore.isAncestor(contentRoot, vcsRoot, strict)) {
        RecursiveFilePathSet dirsByRoot = myDirtyDirectoriesRecursively.get(contentRoot);
        if (dirsByRoot.hasAncestor(path)) {
          return true;
        }
      }
    }

    if (!myDirtyFiles.isEmpty()) {
      if (isInDirtyFiles(path, vcsRoot)) return true;

      FilePath parent = path.getParentPath();
      if (parent != null && isInDirtyFiles(parent, !pathIsRoot ? vcsRoot : null)) return true;
    }

    return false;
  }

  private boolean isInDirtyFiles(@NotNull FilePath path, @Nullable VirtualFile vcsRoot) {
    if (vcsRoot == null) {
      VcsRoot rootObject = myVcsManager.getVcsRootObjectFor(path);
      if (rootObject == null || !myVcs.equals(rootObject.getVcs())) return false;
      vcsRoot = rootObject.getPath();
    }

    final THashSet<FilePath> files = myDirtyFiles.get(vcsRoot);
    return files != null && files.contains(path);
  }

  @Override @NonNls
  public String toString() {
    @NonNls StringBuilder result = new StringBuilder("VcsDirtyScope[");
    if (!myDirtyFiles.isEmpty()) {
      result.append(" files: ");
      for (THashSet<FilePath> paths : myDirtyFiles.values()) {
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
    for (VirtualFile contentRoot : myAffectedContentRoots) {
      result.append(contentRoot.getPath()).append(" ");
    }
    result.append("]");
    return result.toString();
  }

  @Override
  public boolean wasEveryThingDirty() {
    return myWasEverythingDirty;
  }
}
