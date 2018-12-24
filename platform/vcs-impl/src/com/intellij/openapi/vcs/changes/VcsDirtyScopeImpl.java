// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.notNullize;

/**
 * @author max
 * @author yole
 */
public class VcsDirtyScopeImpl extends VcsModifiableDirtyScope {
  private static final TObjectHashingStrategy<FilePath> CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY =
    new TObjectHashingStrategy<FilePath>() {
      @Override
      public int computeHashCode(@Nullable FilePath path) {
        if (path == null) return 0;
        return Objects.hash(path.getPath(), path.isDirectory(), path.isNonLocal());
      }

      @Override
      public boolean equals(@Nullable FilePath path1, @Nullable FilePath path2) {
        if (path1 == null || path2 == null) return path1 == path2;
        return path1.isDirectory() == path2.isDirectory() &&
               path1.isNonLocal() == path2.isNonLocal() &&
               path1.getPath().equals(path2.getPath());
      }
    };
  private final Map<VirtualFile, THashSet<FilePath>> myDirtyFiles = new HashMap<>();
  private final Map<VirtualFile, THashSet<FilePath>> myDirtyDirectoriesRecursively = new HashMap<>();
  private final Set<VirtualFile> myAffectedContentRoots = new THashSet<>();
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final AbstractVcs myVcs;
  private final VcsDirtyScopeModifier myVcsDirtyScopeModifier;
  private final boolean myWasEverythingDirty;

  public VcsDirtyScopeImpl(final AbstractVcs vcs, final Project project) {
    this(vcs, project, false);
  }

  public VcsDirtyScopeImpl(final AbstractVcs vcs, final Project project, boolean wasEverythingDirty) {
    myProject = project;
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myWasEverythingDirty = wasEverythingDirty;
    myVcsDirtyScopeModifier = new VcsDirtyScopeModifier() {
      @NotNull
      @Override
      public Iterator<FilePath> getDirtyFilesIterator() {
        return iterateMap(myDirtyFiles);
      }

      @NotNull
      @Override
      public Iterator<FilePath> getDirtyDirectoriesIterator() {
        return iterateMap(myDirtyDirectoriesRecursively);
      }

      @Override
      public void recheckDirtyKeys() {
        recheckMap(myDirtyDirectoriesRecursively);
        recheckMap(myDirtyFiles);
      }

      @NotNull
      private Iterator<FilePath> iterateMap(@NotNull Map<VirtualFile, THashSet<FilePath>> map) {
        return ContainerUtil.concatIterators(ContainerUtil.map(map.values(), THashSet::iterator));
      }

      private void recheckMap(@NotNull Map<VirtualFile, THashSet<FilePath>> map) {
        for (Iterator<THashSet<FilePath>> iterator = map.values().iterator(); iterator.hasNext();) {
          final THashSet<FilePath> next = iterator.next();
          if (next.isEmpty()) {
            iterator.remove();
          }
        }
      }
    };
  }

  @Override
  public Collection<VirtualFile> getAffectedContentRoots() {
    return myAffectedContentRoots;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

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
          for(VirtualFile child: vFile.getChildren()) {
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
    for(THashSet<FilePath> dirsByRoot: myDirtyDirectoriesRecursively.values()) {
      result.addAll(dirsByRoot);
    }
    return result;
  }

  @Override
  public boolean isRecursivelyDirty(final VirtualFile vf) {
    for(THashSet<FilePath> dirsByRoot: myDirtyDirectoriesRecursively.values()) {
      for (FilePath dir : dirsByRoot) {
        final VirtualFile dirVf = dir.getVirtualFile();
        if (dirVf != null) {
          if (VfsUtilCore.isAncestor(dirVf, vf, false)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public void addDirtyData(@NotNull Collection<? extends FilePath> dirs, @NotNull Collection<? extends FilePath> files) {
    Map<VirtualFile, THashSet<FilePath>> perRootDirs = new HashMap<>(); // recursive
    Map<VirtualFile, THashSet<FilePath>> perRootFiles = new HashMap<>(); // non-recursive

    for (Map.Entry<VirtualFile, THashSet<FilePath>> entry : myDirtyDirectoriesRecursively.entrySet()) {
      perRootDirs.put(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<VirtualFile, THashSet<FilePath>> entry : myDirtyFiles.entrySet()) {
      perRootFiles.put(entry.getKey(), entry.getValue());
    }

    addFilePathsToMap(dirs, perRootDirs);
    addFilePathsToMap(files, perRootFiles);


    myAffectedContentRoots.addAll(perRootDirs.keySet());
    myAffectedContentRoots.addAll(perRootFiles.keySet());


    for (VirtualFile root : myAffectedContentRoots) {
      Set<FilePath> rootDirs = notNullize(perRootDirs.get(root));
      Set<FilePath> rootFiles = notNullize(perRootFiles.get(root));

      THashSet<FilePath> filteredDirs = removeAncestorsRecursive(rootDirs);
      THashSet<FilePath> filteredFiles = removeAncestorsNonRecursive(filteredDirs, rootFiles);

      myDirtyDirectoriesRecursively.put(root, filteredDirs);
      myDirtyFiles.put(root, filteredFiles);
    }
  }

  private void addFilePathsToMap(@NotNull Collection<? extends FilePath> paths, @NotNull Map<VirtualFile, THashSet<FilePath>> pathsMap) {
    for (FilePath dir : paths) {
      VirtualFile vcsRoot = myVcsManager.getVcsRootFor(dir);
      if (vcsRoot == null) continue;
      THashSet<FilePath> set = pathsMap.get(vcsRoot);
      if (set == null) pathsMap.put(vcsRoot, set = newFilePathsSet());
      set.add(dir);
    }
  }

  @NotNull
  private static THashSet<FilePath> removeAncestorsRecursive(@NotNull Collection<? extends FilePath> dirs) {
    List<FilePath> paths = ContainerUtil.sorted(dirs, Comparator.comparingInt(it -> it.getPath().length()));

    THashSet<FilePath> result = newFilePathsSet();
    for (FilePath path : paths) {
      if (hasAncestor(result, path)) continue;
      result.add(path);
    }
    return result;
  }

  @NotNull
  private static THashSet<FilePath> removeAncestorsNonRecursive(@NotNull Set<? extends FilePath> dirs, @NotNull Set<? extends FilePath> files) {
    THashSet<FilePath> result = newFilePathsSet();
    for (FilePath file : files) {
      if (hasAncestor(dirs, file)) continue;
      // if dir non-recursively + non-recursive file child -> can be truncated to dir only
      if (!file.isDirectory() && files.contains(file.getParentPath())) continue;
      result.add(file);
    }
    return result;
  }

  private static boolean hasAncestor(@NotNull Set<? extends FilePath> dirs, @NotNull FilePath filePath) {
    for (FilePath parent : dirs) {
      if (isAncestor(filePath, parent)) return true;
    }
    return false;
  }

  private static boolean isAncestor(@NotNull FilePath filePath, @NotNull FilePath parent) {
    return FileUtil.startsWith(filePath.getPath(), parent.getPath());
  }

  @NotNull
  private static THashSet<FilePath> newFilePathsSet() {
    return new THashSet<>(CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY);
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
          for (Iterator<FilePath> it = files.iterator(); it.hasNext();) {
            FilePath oldBoy = it.next();
            if (isAncestor(oldBoy, newcomer)) {
              it.remove();
            }
          }
        }
      }
    }

    THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
    if (dirsByRoot == null) {
      dirsByRoot = newFilePathsSet();
      myDirtyDirectoriesRecursively.put(vcsRoot, dirsByRoot);
    }
    else {
      for (Iterator<FilePath> it = dirsByRoot.iterator(); it.hasNext();) {
        FilePath oldBoy = it.next();
        if (isAncestor(newcomer, oldBoy)) {
          return;
        }

        if (isAncestor(oldBoy, newcomer)) {
          it.remove();
        }
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

    THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
    if (dirsByRoot != null && hasAncestor(dirsByRoot, newcomer)) {
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
          if (!oldBoy.isDirectory() && CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(oldBoy.getParentPath(), newcomer)) {
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
      THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(root);
      if (dirsByRoot != null) {
        for (FilePath dir : dirsByRoot) {
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
      THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(root);
      if (dirsByRoot != null) {
        for (FilePath dir : dirsByRoot) {
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
  public boolean belongsTo(final FilePath path, final Consumer<? super AbstractVcs> vcsConsumer) {
    if (myProject.isDisposed()) return false;
    final VcsRoot rootObject = myVcsManager.getVcsRootObjectFor(path);
    if (vcsConsumer != null && rootObject != null) {
      vcsConsumer.consume(rootObject.getVcs());
    }
    if (rootObject == null || rootObject.getVcs() != myVcs) {
      return false;
    }

    final VirtualFile vcsRoot = rootObject.getPath();
    if (vcsRoot != null) {
      boolean pathIsRoot = vcsRoot.equals(path.getVirtualFile());
      for (VirtualFile contentRoot : myAffectedContentRoots) {
        // since we don't know exact dirty mechanics, maybe we have 3 nested mappings like:
        // /root -> vcs1, /root/child -> vcs2, /root/child/inner -> vcs1, and we have file /root/child/inner/file,
        // mapping is detected as vcs1 with root /root/child/inner, but we could possibly have in scope
        // "affected root" -> /root with scope = /root recursively
        boolean strict = pathIsRoot && !myVcs.areDirectoriesVersionedItems();
        if (VfsUtilCore.isAncestor(contentRoot, vcsRoot, strict)) {
          THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(contentRoot);
          if (dirsByRoot != null && hasAncestor(dirsByRoot, path)) {
            return true;
          }
        }
      }
    }

    if (!myDirtyFiles.isEmpty()) {
      FilePath parent = path.getParentPath();
      return isInDirtyFiles(path) || isInDirtyFiles(parent);
    }

    return false;
  }

  private boolean isInDirtyFiles(final FilePath path) {
    final VcsRoot rootObject = myVcsManager.getVcsRootObjectFor(path);
    if (rootObject != null && myVcs.equals(rootObject.getVcs())) {
      final THashSet<FilePath> files = myDirtyFiles.get(rootObject.getPath());
      if (files != null && files.contains(path)) return true;
    }
    return false;
  }

  @Override
  public boolean belongsTo(final FilePath path) {
    return belongsTo(path, null);
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
      for(THashSet<FilePath> dirsByRoot: myDirtyDirectoriesRecursively.values()) {
        for(FilePath file: dirsByRoot) {
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
  public VcsDirtyScopeModifier getModifier() {
    return myVcsDirtyScopeModifier;
  }

  @Override
  public boolean wasEveryThingDirty() {
    return myWasEverythingDirty;
  }
}
