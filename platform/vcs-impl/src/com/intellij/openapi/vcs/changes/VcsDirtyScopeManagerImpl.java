// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class VcsDirtyScopeManagerImpl extends VcsDirtyScopeManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(VcsDirtyScopeManagerImpl.class);

  private final Project myProject;

  private final DirtBuilder myDirtBuilder;
  @Nullable private DirtBuilder myDirtInProgress;

  private boolean myReady;
  private final Object LOCK = new Object();

  public VcsDirtyScopeManagerImpl(Project project) {
    myProject = project;

    myDirtBuilder = new DirtBuilder();

    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> markEverythingDirty(), ModalityState.NON_MODAL, myProject.getDisposed());
      }
    });
  }

  private static ProjectLevelVcsManager getVcsManager(@NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project);
  }

  @Override
  public void projectOpened() {
    ProjectLevelVcsManagerImpl.getInstanceImpl(myProject).addInitializationRequest(VcsInitObject.DIRTY_SCOPE_MANAGER, () -> {
      ReadAction.run(() -> {
        boolean ready = !myProject.isDisposed() && myProject.isOpen();
        synchronized (LOCK) {
          myReady = ready;
        }
        if (ready) {
          VcsDirtyScopeVfsListener.install(myProject);
          markEverythingDirty();
        }
      });
    });
  }

  @Override
  public void markEverythingDirty() {
    if ((!myProject.isOpen()) || myProject.isDisposed() || getVcsManager(myProject).getAllActiveVcss().length == 0) {
      return;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("everything dirty: " + findFirstInterestingCallerClass());
    }

    synchronized (LOCK) {
      if (myReady) {
        myDirtBuilder.everythingDirty();
      }
    }

    ChangeListManager.getInstance(myProject).scheduleUpdate();
  }

  @Override
  public void disposeComponent() {
    synchronized (LOCK) {
      myReady = false;
      myDirtBuilder.reset();
      myDirtInProgress = null;
    }
  }

  @NotNull
  private MultiMap<AbstractVcs, FilePath> groupByVcs(@Nullable Collection<? extends FilePath> from) {
    if (from == null) {
      return MultiMap.empty();
    }

    MultiMap<AbstractVcs, FilePath> map = MultiMap.createSet();
    for (FilePath path : from) {
      AbstractVcs vcs = getVcsManager(myProject).getVcsFor(path);
      if (vcs != null) {
        map.putValue(vcs, path);
      }
    }
    return map;
  }

  @NotNull
  private MultiMap<AbstractVcs, FilePath> groupFilesByVcs(@Nullable final Collection<? extends VirtualFile> from) {
    if (from == null) return MultiMap.empty();
    MultiMap<AbstractVcs, FilePath> map = MultiMap.createSet();
    for (VirtualFile file : from) {
      AbstractVcs vcs = getVcsManager(myProject).getVcsFor(file);
      if (vcs != null) {
        map.putValue(vcs, VcsUtil.getFilePath(file));
      }
    }
    return map;
  }

  private void fileVcsPathsDirty(@NotNull MultiMap<AbstractVcs, FilePath> filesConverted,
                                 @NotNull MultiMap<AbstractVcs, FilePath> dirsConverted) {
    if (filesConverted.isEmpty() && dirsConverted.isEmpty()) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("dirty files: %s; dirty dirs: %s; %s",
                              toString(filesConverted), toString(dirsConverted), findFirstInterestingCallerClass()));
    }

    boolean hasSomethingDirty;
    synchronized (LOCK) {
      if (!myReady) return;
      markDirty(myDirtBuilder, filesConverted, false);
      markDirty(myDirtBuilder, dirsConverted, true);
      hasSomethingDirty = !myDirtBuilder.isEmpty();
    }

    if (hasSomethingDirty) {
      ChangeListManager.getInstance(myProject).scheduleUpdate();
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

  @Override
  public void filePathsDirty(@Nullable final Collection<? extends FilePath> filesDirty, @Nullable final Collection<? extends FilePath> dirsRecursivelyDirty) {
    try {
      fileVcsPathsDirty(groupByVcs(filesDirty), groupByVcs(dirsRecursivelyDirty));
    }
    catch (ProcessCanceledException ignore) {
    }
  }

  @Override
  public void filesDirty(@Nullable final Collection<? extends VirtualFile> filesDirty, @Nullable final Collection<? extends VirtualFile> dirsRecursivelyDirty) {
    try {
      fileVcsPathsDirty(groupFilesByVcs(filesDirty), groupFilesByVcs(dirsRecursivelyDirty));
    }
    catch (ProcessCanceledException ignore) {
    }
  }

  @NotNull
  private static Collection<FilePath> toFilePaths(@Nullable Collection<? extends VirtualFile> files) {
    if (files == null) {
      return Collections.emptyList();
    }
    return ContainerUtil.map(files, virtualFile -> VcsUtil.getFilePath(virtualFile));
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
  public void dirDirtyRecursively(@NotNull final VirtualFile dir) {
    dirDirtyRecursively(VcsUtil.getFilePath(dir));
  }

  @Override
  public void dirDirtyRecursively(@NotNull final FilePath path) {
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
    boolean isEverythingDirty = dirt.isEverythingDirty();
    if (isEverythingDirty) {
      dirs.putAllValues(getEverythingDirtyRoots());
    }
    Set<AbstractVcs> keys = ContainerUtil.union(files.keySet(), dirs.keySet());

    Map<AbstractVcs, VcsDirtyScopeImpl> scopes = new HashMap<>();
    for (AbstractVcs key : keys) {
      VcsDirtyScopeImpl scope = new VcsDirtyScopeImpl(key, myProject, isEverythingDirty);
      scopes.put(key, scope);
      scope.addDirtyData(dirs.get(key), files.get(key));
    }

    return new VcsInvalidated(new ArrayList<>(scopes.values()), isEverythingDirty);
  }

  @NotNull
  private MultiMap<AbstractVcs, FilePath> getEverythingDirtyRoots() {
    MultiMap<AbstractVcs, FilePath> dirtyRoots = MultiMap.createSet();

    VcsRoot[] roots = getVcsManager(myProject).getAllVcsRoots();
    for (VcsRoot root : roots) {
      AbstractVcs vcs = root.getVcs();
      VirtualFile path = root.getPath();
      if (vcs != null) {
        dirtyRoots.putValue(vcs, VcsUtil.getFilePath(path));
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
  public Collection<FilePath> whatFilesDirty(@NotNull final Collection<? extends FilePath> files) {
    DirtBuilder dirtBuilder;
    DirtBuilder dirtBuilderInProgress;
    synchronized (LOCK) {
      if (!myReady) return Collections.emptyList();
      dirtBuilder = new DirtBuilder(myDirtBuilder);
      dirtBuilderInProgress = myDirtInProgress != null ? new DirtBuilder(myDirtInProgress) : new DirtBuilder();
    }

    VcsInvalidated invalidated = calculateInvalidated(dirtBuilder);
    VcsInvalidated inProgress = calculateInvalidated(dirtBuilderInProgress);
    Collection<FilePath> result = new ArrayList<>();
    for (FilePath fp : files) {
      if (invalidated.isFileDirty(fp) || inProgress.isFileDirty(fp)) {
        result.add(fp);
      }
    }
    return result;
  }

  @NotNull
  private static String toString(@NotNull MultiMap<AbstractVcs, FilePath> filesByVcs) {
    return StringUtil.join(filesByVcs.keySet(), vcs -> vcs.getName() + ": " + StringUtil.join(filesByVcs.get(vcs), path -> path.getPath(), "\n"), "\n");
  }

  @Nullable
  private static Class<?> findFirstInterestingCallerClass() {
    for (int i = 1; i <= 5; i++) {
      Class<?> clazz = ReflectionUtil.findCallerClass(i);
      if (clazz == null || !clazz.getName().contains(VcsDirtyScopeManagerImpl.class.getName())) return clazz;
    }
    return null;
  }
}
