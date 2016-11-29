/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class VcsDirtyScopeManagerImpl extends VcsDirtyScopeManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl");

  private final Project myProject;
  private final ChangeListManager myChangeListManager;
  private final ProjectLevelVcsManagerImpl myVcsManager;
  private final VcsGuess myGuess;

  private final DirtBuilder myDirtBuilder;
  @Nullable private DirtBuilder myDirtInProgress;

  private boolean myReady;
  private final Object LOCK = new Object();

  public VcsDirtyScopeManagerImpl(Project project, ChangeListManager changeListManager, ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myChangeListManager = changeListManager;
    myVcsManager = (ProjectLevelVcsManagerImpl)vcsManager;

    myGuess = new VcsGuess(myProject);
    myDirtBuilder = new DirtBuilder(myGuess);

    ((ChangeListManagerImpl) myChangeListManager).setDirtyScopeManager(this);
  }

  @Override
  public void projectOpened() {
    myVcsManager.addInitializationRequest(VcsInitObject.DIRTY_SCOPE_MANAGER, new Runnable() {
      @Override
      public void run() {
        boolean ready = false;
        synchronized (LOCK) {
          if (!myProject.isDisposed()) {
            myReady = ready = true;
          }
        }
        if (ready) {
          VcsDirtyScopeVfsListener.install(myProject);
          markEverythingDirty();
        }
      }
    });
  }

  public void markEverythingDirty() {
    if ((! myProject.isOpen()) || myProject.isDisposed() || myVcsManager.getAllActiveVcss().length == 0) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("everything dirty: " + findFirstInterestingCallerClass());
    }

    synchronized (LOCK) {
      if (myReady) {
        myDirtBuilder.everythingDirty();
      }
    }

    myChangeListManager.scheduleUpdate();
  }

  @Override
  public void projectClosed() {
  }

  @Override
  @NotNull @NonNls
  public String getComponentName() {
    return "VcsDirtyScopeManager";
  }

  @Override
  public void initComponent() {}

  public void disposeComponent() {
    synchronized (LOCK) {
      myReady = false;
      myDirtBuilder.reset();
      myDirtInProgress = null;
    }
  }

  @NotNull
  private MultiMap<AbstractVcs, FilePath> groupByVcs(@Nullable final Collection<FilePath> from) {
    if (from == null) return MultiMap.empty();
    MultiMap<AbstractVcs, FilePath> map = MultiMap.createSet();
    for (FilePath path : from) {
      AbstractVcs vcs = myGuess.getVcsForDirty(path);
      if (vcs != null) {
        map.putValue(vcs, path);
      }
    }
    return map;
  }

  @Override
  public void filePathsDirty(@Nullable final Collection<FilePath> filesDirty, @Nullable final Collection<FilePath> dirsRecursivelyDirty) {
    try {
      final MultiMap<AbstractVcs, FilePath> filesConverted = groupByVcs(filesDirty);
      final MultiMap<AbstractVcs, FilePath> dirsConverted = groupByVcs(dirsRecursivelyDirty);
      if (filesConverted.isEmpty() && dirsConverted.isEmpty()) return;

      if (LOG.isDebugEnabled()) {
        LOG.debug("dirty files: " + toString(filesConverted) + "; dirty dirs: " + toString(dirsConverted) + "; " + findFirstInterestingCallerClass());
      }

      boolean hasSomethingDirty;
      synchronized (LOCK) {
        if (!myReady) return;
        markDirty(myDirtBuilder, filesConverted, false);
        markDirty(myDirtBuilder, dirsConverted, true);
        hasSomethingDirty = !myDirtBuilder.isEmpty();
      }

      if (hasSomethingDirty) {
        myChangeListManager.scheduleUpdate();
      }
    }
    catch (ProcessCanceledException ignore) {
    }
  }

  private static void markDirty(@NotNull DirtBuilder dirtBuilder,
                                @NotNull MultiMap<AbstractVcs, FilePath> filesOrDirs,
                                boolean recursively) {
    for (AbstractVcs vcs : filesOrDirs.keySet()) {
      for (FilePath path : filesOrDirs.get(vcs)) {
        if (recursively) {
          dirtBuilder.addDirtyDirRecursively(vcs, path);
        }
        else {
          dirtBuilder.addDirtyFile(vcs, path);
        }
      }
    }
  }

  public void filesDirty(@Nullable final Collection<VirtualFile> filesDirty, @Nullable final Collection<VirtualFile> dirsRecursivelyDirty) {
    filePathsDirty(toFilePaths(filesDirty), toFilePaths(dirsRecursivelyDirty));
  }

  @NotNull
  private static Collection<FilePath> toFilePaths(@Nullable Collection<VirtualFile> files) {
    if (files == null) return Collections.emptyList();
    return ContainerUtil.map(files, new Function<VirtualFile, FilePath>() {
      @Override
      public FilePath fun(VirtualFile virtualFile) {
        return VcsUtil.getFilePath(virtualFile);
      }
    });
  }

  @Override
  public void fileDirty(@NotNull final VirtualFile file) {
    fileDirty(VcsUtil.getFilePath(file));
  }

  @Override
  public void fileDirty(@NotNull final FilePath file) {
    filePathsDirty(Collections.singleton(file), null);
  }

  @Override
  public void dirDirtyRecursively(final VirtualFile dir, final boolean scheduleUpdate) {
    dirDirtyRecursively(dir);
  }

  @Override
  public void dirDirtyRecursively(final VirtualFile dir) {
    dirDirtyRecursively(VcsUtil.getFilePath(dir));
  }

  @Override
  public void dirDirtyRecursively(final FilePath path) {
    filePathsDirty(null, Collections.singleton(path));
  }

  @Override
  @Nullable
  public VcsInvalidated retrieveScopes() {
    DirtBuilder dirtBuilder;
    synchronized (LOCK) {
      if (!myReady) return null;
      dirtBuilder = new DirtBuilder(myDirtBuilder);
      myDirtInProgress = dirtBuilder;
      myDirtBuilder.reset();
    }
    return calculateInvalidated(dirtBuilder);
  }

  @NotNull
  private VcsInvalidated calculateInvalidated(@NotNull DirtBuilder dirt) {
    MultiMap<AbstractVcs, FilePath> files = dirt.getFilesForVcs();
    MultiMap<AbstractVcs, FilePath> dirs = dirt.getDirsForVcs();
    if (dirt.isEverythingDirty()) {
      dirs.putAllValues(getEverythingDirtyRoots());
    }
    Set<AbstractVcs> keys = ContainerUtil.union(files.keySet(), dirs.keySet());

    Map<AbstractVcs, VcsDirtyScopeImpl> scopes = ContainerUtil.newHashMap();
    for (AbstractVcs key : keys) {
      VcsDirtyScopeImpl scope = new VcsDirtyScopeImpl(key, myProject);
      scopes.put(key, scope);
      scope.addDirtyData(dirs.get(key), files.get(key));
    }

    return new VcsInvalidated(new ArrayList<>(scopes.values()), dirt.isEverythingDirty());
  }

  @NotNull
  private MultiMap<AbstractVcs, FilePath> getEverythingDirtyRoots() {
    MultiMap<AbstractVcs, FilePath> dirtyRoots = MultiMap.createSet();
    dirtyRoots.putAllValues(groupByVcs(toFilePaths(DefaultVcsRootPolicy.getInstance(myProject).getDirtyRoots())));

    List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings();
    for (VcsDirectoryMapping mapping : mappings) {
      if (!mapping.isDefaultMapping() && mapping.getVcs() != null) {
        AbstractVcs vcs = myVcsManager.findVcsByName(mapping.getVcs());
        if (vcs != null) {
          dirtyRoots.putValue(vcs, VcsUtil.getFilePath(mapping.getDirectory(), true));
        }
      }
    }
    return dirtyRoots;
  }

  @Override
  public void changesProcessed() {
    synchronized (LOCK) {
      myDirtInProgress = null;
    }
  }

  @NotNull
  @Override
  public Collection<FilePath> whatFilesDirty(@NotNull final Collection<FilePath> files) {
    DirtBuilder dirtBuilder;
    DirtBuilder dirtBuilderInProgress;
    synchronized (LOCK) {
      if (!myReady) return Collections.emptyList();
      dirtBuilder = new DirtBuilder(myDirtBuilder);
      dirtBuilderInProgress = myDirtInProgress != null ? new DirtBuilder(myDirtInProgress) : new DirtBuilder(myGuess);
    }

    VcsInvalidated invalidated = calculateInvalidated(dirtBuilder);
    VcsInvalidated inProgress = calculateInvalidated(dirtBuilderInProgress);
    Collection<FilePath> result = ContainerUtil.newArrayList();
    for (FilePath fp : files) {
      if (invalidated.isFileDirty(fp) || inProgress.isFileDirty(fp)) {
        result.add(fp);
      }
    }
    return result;
  }

  @NotNull
  private static String toString(@NotNull final MultiMap<AbstractVcs, FilePath> filesByVcs) {
    return StringUtil.join(filesByVcs.keySet(), new Function<AbstractVcs, String>() {
      @Override
      public String fun(@NotNull AbstractVcs vcs) {
        return vcs.getName() + ": " + StringUtil.join(filesByVcs.get(vcs), new Function<FilePath, String>() {
          @Override
          public String fun(@NotNull FilePath path) {
            return path.getPath();
          }
        }, "\n");
      }
    }, "\n");
  }

  @Nullable
  private static Class findFirstInterestingCallerClass() {
    for (int i = 1; i <= 5; i++) {
      Class clazz = ReflectionUtil.findCallerClass(i);
      if (clazz == null || !clazz.getName().contains(VcsDirtyScopeManagerImpl.class.getName())) return clazz;
    }
    return null;
  }
}
