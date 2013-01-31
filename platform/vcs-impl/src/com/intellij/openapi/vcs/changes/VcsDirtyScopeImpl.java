/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 * @author yole
 */
public class VcsDirtyScopeImpl extends VcsModifiableDirtyScope {
  private final Map<VirtualFile, THashSet<FilePath>> myDirtyFiles = new HashMap<VirtualFile, THashSet<FilePath>>();
  private final Map<VirtualFile, THashSet<FilePath>> myDirtyDirectoriesRecursively = new HashMap<VirtualFile, THashSet<FilePath>>();
  private final Set<VirtualFile> myAffectedContentRoots = new THashSet<VirtualFile>();
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final AbstractVcs myVcs;
  private VcsDirtyScopeModifier myVcsDirtyScopeModifier;
  private boolean myWasEverythingDirty;

  public VcsDirtyScopeImpl(final AbstractVcs vcs, final Project project) {
    myProject = project;
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myWasEverythingDirty = false;
    myVcsDirtyScopeModifier = new VcsDirtyScopeModifier() {
      @Override
      public Collection<VirtualFile> getAffectedVcsRoots() {
        return Collections.unmodifiableCollection(myDirtyDirectoriesRecursively.keySet());
      }

      @Override
      public Iterator<FilePath> getDirtyFilesIterator() {
        if (myDirtyFiles.isEmpty()) {
          return Collections.<FilePath>emptyList().iterator();
        }
        final ArrayList<Iterator<FilePath>> iteratorList = new ArrayList<Iterator<FilePath>>(myDirtyFiles.size());
        for (THashSet<FilePath> paths : myDirtyFiles.values()) {
          iteratorList.add(paths.iterator());
        }
        return ContainerUtil.concatIterators(iteratorList);
      }

      @Nullable
      @Override
      public Iterator<FilePath> getDirtyDirectoriesIterator(final VirtualFile root) {
        final THashSet<FilePath> filePaths = myDirtyDirectoriesRecursively.get(root);
        if (filePaths != null) {
          return filePaths.iterator();
        }
        return null;
      }

      @Override
      public void recheckDirtyKeys() {
        recheckMap(myDirtyDirectoriesRecursively);
        recheckMap(myDirtyFiles);
      }

      private void recheckMap(Map<VirtualFile, THashSet<FilePath>> map) {
        for (Iterator<THashSet<FilePath>> iterator = map.values().iterator(); iterator.hasNext();) {
          final THashSet<FilePath> next = iterator.next();
          if (next.isEmpty()) {
            iterator.remove();
          }
        }
      }
    };
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
    final THashSet<FilePath> result = new THashSet<FilePath>();
    for (THashSet<FilePath> paths : myDirtyFiles.values()) {
      result.addAll(paths);
    }
    for (THashSet<FilePath> paths : myDirtyFiles.values()) {
      for (FilePath filePath : paths) {
        VirtualFile vFile = filePath.getVirtualFile();
        if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
          for(VirtualFile child: vFile.getChildren()) {
            result.add(new FilePathImpl(child));
          }
        }
      }
    }
    return result;
  }

  public Set<FilePath> getDirtyFilesNoExpand() {
    final THashSet<FilePath> paths = new THashSet<FilePath>();
    for (THashSet<FilePath> filePaths : myDirtyFiles.values()) {
      paths.addAll(filePaths);
    }
    return paths;
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

  private static class FileOrDir {
    private final FilePath myPath;
    private final boolean myRecursive;

    private FileOrDir(FilePath path, boolean recursive) {
      myPath = path;
      myRecursive = recursive;
    }
  }

  public void addDirtyData(final Collection<FilePath> dirs, final Collection<FilePath> files) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final HashSet<FilePath> newFiles = new HashSet<FilePath>(files);
        newFiles.removeAll(dirs); // if the same dir is added recursively and not recursively, prefer recursive mark

        final MultiMap<VirtualFile, FileOrDir> perRoot = new MultiMap<VirtualFile, FileOrDir>() {
          @Override
          protected Collection<FileOrDir> createCollection() {
            return new THashSet<FileOrDir>();
          }
        };
        for (Map.Entry<VirtualFile, THashSet<FilePath>> entry : myDirtyDirectoriesRecursively.entrySet()) {
          newFiles.removeAll(entry.getValue()); // if the same dir is added recursively and not recursively, prefer recursive mark
          for (FilePath path : entry.getValue()) {
            perRoot.putValue(entry.getKey(), new FileOrDir(path, true));
          }
        }

        for (Map.Entry<VirtualFile, THashSet<FilePath>> entry : myDirtyFiles.entrySet()) {
          for (FilePath path : entry.getValue()) {
            perRoot.putValue(entry.getKey(), new FileOrDir(path, false));
          }
        }

        for (FilePath dir : dirs) {
          addFilePathToMap(perRoot, dir, true);
        }
        for (FilePath file : newFiles) {
          addFilePathToMap(perRoot, file, false);
        }

        for (Map.Entry<VirtualFile, Collection<FileOrDir>> entry : perRoot.entrySet()) {
          final Collection<FileOrDir> set = entry.getValue();
          final Collection<FileOrDir> newCollection = FileUtil.removeAncestors(set, new Convertor<FileOrDir, String>() {
            @Override
            public String convert(FileOrDir o) {
              return o.myPath.getPath();
            }
          }, new PairProcessor<FileOrDir, FileOrDir>() {
              @Override
              public boolean process(FileOrDir parent, FileOrDir child) {
                if (! parent.myRecursive) {// if under non-recursive dirty dir, generally do not remove child with one exception...
                  if (! child.myRecursive && ! child.myPath.isDirectory()) {
                    if (Comparing.equal(child.myPath.getParentPath(), parent.myPath)) {
                      return true; // only if dir non-recursively + non-recursive file child -> can be truncated to dir only
                    }
                  }
                  return false;
                }
                return true;
              }
          });
          set.retainAll(newCollection);
        }

        myAffectedContentRoots.addAll(perRoot.keySet());
        for (Map.Entry<VirtualFile, Collection<FileOrDir>> entry : perRoot.entrySet()) {
          final VirtualFile root = entry.getKey();
          final THashSet<FilePath> curFiles = new THashSet<FilePath>();
          final THashSet<FilePath> curDirs = new THashSet<FilePath>();
          final Collection<FileOrDir> value = entry.getValue();
          for (FileOrDir fileOrDir : value) {
            if (fileOrDir.myRecursive) {
              curDirs.add(fileOrDir.myPath);
            } else {
              curFiles.add(fileOrDir.myPath);
            }
          }
          // no clear is necessary since no root can disappear
          // also, we replace contents, so here's no merging
          if (! curDirs.isEmpty()) {
            myDirtyDirectoriesRecursively.put(root, curDirs);
          }
          if (! curFiles.isEmpty()) {
            myDirtyFiles.put(root, curFiles);
          }
        }
      }
    });
  }

  private void addFilePathToMap(MultiMap<VirtualFile, FileOrDir> perRoot, FilePath dir, final boolean recursively) {
    final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(dir);
    if (vcsRoot != null) {
      perRoot.putValue(vcsRoot, new FileOrDir(dir, recursively));
    }
  }

  /**
   * Add dirty directory recursively. If there are already dirty entries
   * that are descendants or ancestors for the added directory, the contained
   * entries are dropped from scope.
   *
   * @param newcomer a new directory to add
   */
  public void addDirtyDirRecursively(final FilePath newcomer) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(newcomer);
        if (vcsRoot == null) return;
        myAffectedContentRoots.add(vcsRoot);

        for (Map.Entry<VirtualFile, THashSet<FilePath>> entry : myDirtyFiles.entrySet()) {
          final VirtualFile groupRoot = entry.getKey();
          if (VfsUtilCore.isAncestor(vcsRoot, groupRoot, false)) {
            final THashSet<FilePath> files = entry.getValue();
            if (files != null) {
              for (Iterator<FilePath> it = files.iterator(); it.hasNext();) {
                FilePath oldBoy = it.next();
                if (oldBoy.isUnder(newcomer, false)) {
                  it.remove();
                }
              }
            }
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

        final THashSet<FilePath> dirtyFiles = myDirtyFiles.get(vcsRoot);
        if (dirtyFiles == null) {
          final THashSet<FilePath> set = new THashSet<FilePath>();
          set.add(newcomer);
          myDirtyFiles.put(vcsRoot, set);
        } else {
          if (newcomer.isDirectory()) {
            for (Iterator<FilePath> iterator = dirtyFiles.iterator(); iterator.hasNext(); ) {
              final FilePath oldBoy = iterator.next();
              if (!oldBoy.isDirectory() && Comparing.equal(oldBoy.getVirtualFileParent(), newcomer.getVirtualFile())) {
                iterator.remove();
              }
            }
          } else if (dirtyFiles.size() > 0) {
            VirtualFile parent = newcomer.getVirtualFileParent();
            if (parent != null && dirtyFiles.contains(new FilePathImpl(parent))) {
              return;
            }
            dirtyFiles.add(newcomer);
          }
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

    for (VirtualFile root : myAffectedContentRoots) {
      final THashSet<FilePath> files = myDirtyFiles.get(root);
      if (files != null) {
        for (FilePath file : files) {
          iterator.process(file);
          final VirtualFile vFile = file.getVirtualFile();
          if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
            for (VirtualFile child : vFile.getChildren()) {
              iterator.process(new FilePathImpl(child));
            }
          }
        }
      }
    }
  }

  @Override
  public void iterateExistingInsideScope(Processor<VirtualFile> processor) {
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
  public boolean belongsTo(final FilePath path, final Consumer<AbstractVcs> vcsConsumer) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (myProject.isDisposed()) return Boolean.FALSE;
        final VcsRoot rootObject = myVcsManager.getVcsRootObjectFor(path);
        if (vcsConsumer != null && rootObject != null) {
          vcsConsumer.consume(rootObject.getVcs());
        }
        if (rootObject == null || rootObject.getVcs() != myVcs) {
          return Boolean.FALSE;
        }

        final VirtualFile vcsRoot = rootObject.getPath();
        if (vcsRoot != null) {
          for (VirtualFile contentRoot : myAffectedContentRoots) {
            // since we don't know exact dirty mechanics, maybe we have 3 nested mappings like:
            // /root -> vcs1, /root/child -> vcs2, /root/child/inner -> vcs1, and we have file /root/child/inner/file,
            // mapping is detected as vcs1 with root /root/child/inner, but we could possibly have in scope
            // "affected root" -> /root with scope = /root recursively
            if (VfsUtilCore.isAncestor(contentRoot, vcsRoot, false)) {
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
          return isInDirtyFiles(path) || isInDirtyFiles(parent);
        }

        return Boolean.FALSE;
      }
    }).booleanValue();
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
    if (myDirtyFiles.size() > 0) {
      result.append(" files=");
      for (THashSet<FilePath> paths : myDirtyFiles.values()) {
        for (FilePath file : paths) {
          result.append(file).append(" ");
        }
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

  @Override
  public VcsDirtyScopeModifier getModifier() {
    return myVcsDirtyScopeModifier;
  }

  @Override
  public boolean wasEveryThingDirty() {
    return myWasEverythingDirty;
  }

  public void setWasEverythingDirty(boolean wasEverythingDirty) {
    myWasEverythingDirty = wasEverythingDirty;
  }
}
