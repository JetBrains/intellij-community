/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author max
 * @author yole
 */
public class VcsDirtyScopeImpl extends VcsAppendableDirtyScope {
  private final Set<FilePath> myDirtyFiles = new THashSet<FilePath>();
  private final Map<VirtualFile, THashSet<FilePath>> myDirtyDirectoriesRecursively = new HashMap<VirtualFile, THashSet<FilePath>>();
  private final Set<VirtualFile> myAffectedContentRoots = new THashSet<VirtualFile>();
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final AbstractVcs myVcs;

  public VcsDirtyScopeImpl(final AbstractVcs vcs, final Project project) {
    myProject = project;
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  public Collection<VirtualFile> getAffectedContentRoots() {
    return myAffectedContentRoots;
  }

  public Collection<VirtualFile> getAffectedContentRootsWithCheck() {
    if (myVcs.allowsNestedRoots()) {
      final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myVcs.getProject());
      final VirtualFile[] roots = vcsManager.getRootsUnderVcs(myVcs);

      final Set<VirtualFile> result = new HashSet<VirtualFile>(myAffectedContentRoots);
      for (VirtualFile root : roots) {
        for (VirtualFile dir : myDirtyDirectoriesRecursively.keySet()) {
          if (VfsUtil.isAncestor(dir, root, true)) {
            result.add(root);
          }
        }
      }
      return new SmartList<VirtualFile>(result);
    }
    return myAffectedContentRoots;
  }

  public Project getProject() {
    return myProject;
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }

  public Set<FilePath> getDirtyFiles() {
    final THashSet<FilePath> result = new THashSet<FilePath>(myDirtyFiles);
    for(FilePath filePath: myDirtyFiles) {
      VirtualFile vFile = filePath.getVirtualFile();
      if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
        for(VirtualFile child: vFile.getChildren()) {
          result.add(new FilePathImpl(child));
        }
      }
    }
    return result;
  }

  public Set<FilePath> getDirtyFilesNoExpand() {
    return new THashSet<FilePath>(myDirtyFiles);
  }

  public Set<FilePath> getRecursivelyDirtyDirectories() {
    THashSet<FilePath> result = new THashSet<FilePath>();
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
          if (VfsUtil.isAncestor(dirVf, vf, false)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Add dirty directory recursively. If there are already dirty entries
   * that are descendants or ancestors for the added directory, the contained
   * entries are droped from scope.
   *
   * @param newcomer a new directory to add
   */
  public void addDirtyDirRecursively(final FilePath newcomer) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (VcsDirtyScopeImpl.this) {
          final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(newcomer);
          if (vcsRoot == null) return;
          myAffectedContentRoots.add(vcsRoot);

          for (Iterator<FilePath> it = myDirtyFiles.iterator(); it.hasNext();) {
            FilePath oldBoy = it.next();
            if (oldBoy.isUnder(newcomer, false)) {
              it.remove();
            }
          }

          THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
          if (dirsByRoot == null) {
            dirsByRoot = new THashSet<FilePath>();
            myDirtyDirectoriesRecursively.put(vcsRoot, dirsByRoot);
          }
          else {
            for (Iterator<FilePath> it = dirsByRoot.iterator(); it.hasNext();) {
              FilePath oldBoy = it.next();
              if (newcomer.isUnder(oldBoy, false)) {
                return;
              }

              if (oldBoy.isUnder(newcomer, false)) {
                it.remove();
              }
            }
          }

          dirsByRoot.add(newcomer);
        }
      }
    });
  }

  /**
   * Add dirty file to the scope. Note that file is not added
   * if its ancestor was added as dirty recursively or if its parent
   * is in already in the dirty scope. Also immendiate non-directory
   * children are removed from the set of dirty files.
   *
   * @param newcomer a file or directory added to the dirty scope.
   */
  public void addDirtyFile(final FilePath newcomer) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (VcsDirtyScopeImpl.this) {
          final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(newcomer);
          myAffectedContentRoots.add(vcsRoot);

          THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
          if (dirsByRoot != null) {
            for (FilePath oldBoy : dirsByRoot) {
              if (newcomer.isUnder(oldBoy, false)) {
                return;
              }
            }
          }

          if (newcomer.isDirectory()) {
            final List<FilePath> files = new ArrayList<FilePath>(myDirtyFiles);
            for (FilePath oldBoy : files) {
              if (!oldBoy.isDirectory() && oldBoy.getVirtualFileParent() == newcomer.getVirtualFile()) {
                myDirtyFiles.remove(oldBoy);
              }
            }
          }
          else if (myDirtyFiles.size() > 0) {
            VirtualFile parent = newcomer.getVirtualFileParent();
            if (parent != null && myDirtyFiles.contains(new FilePathImpl(parent))) {
              return;
            }
          }

          myDirtyFiles.add(newcomer);
        }
      }
    });
  }

  public void iterate(final Processor<FilePath> iterator) {
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

    for (FilePath file : myDirtyFiles) {
      iterator.process(file);
      final VirtualFile vFile = file.getVirtualFile();
      if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
        for (VirtualFile child : vFile.getChildren()) {
          iterator.process(new FilePathImpl(child));
        }
      }
    }
  }

  public boolean belongsTo(final FilePath path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        synchronized (VcsDirtyScopeImpl.this) {
          if (myProject.isDisposed()) return Boolean.FALSE;
          if (myVcsManager.getVcsFor(path) != myVcs) {
            return Boolean.FALSE;
          }

          final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(path);
          if (vcsRoot != null) {
            for(VirtualFile contentRoot: myAffectedContentRoots) {
              if (VfsUtil.isAncestor(contentRoot, vcsRoot, false)) {
                THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(contentRoot);
                if (dirsByRoot != null) {
                  for (FilePath filePath : dirsByRoot) {
                    if (path.isUnder(filePath, false)) return Boolean.TRUE;
                  }
                }
              }
            }
          }

          if (myDirtyFiles.size() > 0) {
            FilePath parent;
            VirtualFile vParent = path.getVirtualFileParent();
            if (vParent != null && vParent.isValid()) {
              parent = new FilePathImpl(vParent);
            }
            else {
              parent = FilePathImpl.create(path.getIOFile().getParentFile());
            }
            if (myDirtyFiles.contains(parent) || myDirtyFiles.contains(path)) return Boolean.TRUE;
          }

          return Boolean.FALSE;
        }
      }
    }).booleanValue();
  }

  @Override @NonNls
  public String toString() {
    @NonNls StringBuilder result = new StringBuilder("VcsDirtyScope[");
    if (myDirtyFiles.size() > 0) {
      result.append(" files=");
      for(FilePath file: myDirtyFiles) {
        result.append(file).append(" ");
      }
    }
    if (myDirtyDirectoriesRecursively.size() > 0) {
      result.append(" dirs=");
      for(THashSet<FilePath> dirsByRoot: myDirtyDirectoriesRecursively.values()) {
        for(FilePath file: dirsByRoot) {
          result.append(file).append(" ");
        }
      }
    }
    result.append("affected roots=");
    for (VirtualFile contentRoot : myAffectedContentRoots) {
      result.append(contentRoot.getPath()).append(" ");
    }
    result.append("affected roots DISCLOSED=");
    for (VirtualFile contentRoot : getAffectedContentRootsWithCheck()) {
      result.append(contentRoot.getPath()).append(" ");
    }
    result.append("]");
    return result.toString();
  }
}
