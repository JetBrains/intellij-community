// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vcs.impl.VcsStartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class VcsDirtyScopeManagerImpl extends VcsDirtyScopeManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsDirtyScopeManagerImpl.class);

  private final Project myProject;

  @NotNull private DirtBuilder myDirtBuilder = new DirtBuilder();
  @Nullable private DirtBuilder myDirtInProgress;
  @Nullable private ActionCallback myRefreshInProgress;

  private boolean myReady;
  private final Object LOCK = new Object();

  @NotNull
  public static VcsDirtyScopeManagerImpl getInstanceImpl(@NotNull Project project) {
    return ((VcsDirtyScopeManagerImpl)getInstance(project));
  }

  public VcsDirtyScopeManagerImpl(@NotNull Project project) {
    myProject = project;

    MessageBusConnection busConnection = myProject.getMessageBus().connect();
    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> markEverythingDirty(), ModalityState.NON_MODAL, myProject.getDisposed());
      }
    });
  }

  private static ProjectLevelVcsManager getVcsManager(@NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project);
  }

  private void startListenForChanges() {
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
  }

  @Override
  public void markEverythingDirty() {
    if ((!myProject.isOpen()) || myProject.isDisposed() || getVcsManager(myProject).getAllActiveVcss().length == 0) {
      return;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("everything dirty: " + findFirstInterestingCallerClass());
    }

    boolean wasReady;
    ActionCallback ongoingRefresh;
    synchronized (LOCK) {
      wasReady = myReady;
      if (wasReady) {
        myDirtBuilder.markEverythingDirty();
      }
      ongoingRefresh = myRefreshInProgress;
    }
    if (ongoingRefresh != null) ongoingRefresh.setRejected();

    if (wasReady) {
      ChangeListManager.getInstance(myProject).scheduleUpdate();
    }
  }

  @Override
  public void dispose() {
    synchronized (LOCK) {
      myReady = false;
      myDirtBuilder = new DirtBuilder();
      myDirtInProgress = null;
      myRefreshInProgress = null;
    }
  }

  @NotNull
  private Map<VcsRoot, Set<FilePath>> groupByVcs(@Nullable Iterable<? extends FilePath> from) {
    if (from == null) return Collections.emptyMap();

    ProjectLevelVcsManager vcsManager = getVcsManager(myProject);
    Map<VcsRoot, Set<FilePath>> map = new HashMap<>();
    for (FilePath path : from) {
      VcsRoot vcsRoot = vcsManager.getVcsRootObjectFor(path);
      if (vcsRoot != null && vcsRoot.getVcs() != null) {
        Set<FilePath> pathSet = map.computeIfAbsent(vcsRoot, key -> new THashSet<>(getDirtyScopeHashingStrategy(key.getVcs())));
        pathSet.add(path);
      }
    }
    return map;
  }

  @NotNull
  private Map<VcsRoot, Set<FilePath>> groupFilesByVcs(@Nullable Collection<? extends VirtualFile> from) {
    if (from == null) return Collections.emptyMap();
    return groupByVcs(() -> ContainerUtil.mapIterator(from.iterator(), file -> VcsUtil.getFilePath(file)));
  }

  private void fileVcsPathsDirty(@NotNull Map<VcsRoot, Set<FilePath>> filesConverted,
                                 @NotNull Map<VcsRoot, Set<FilePath>> dirsConverted) {
    if (filesConverted.isEmpty() && dirsConverted.isEmpty()) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("dirty files: %s; dirty dirs: %s; %s",
                              toString(filesConverted), toString(dirsConverted), findFirstInterestingCallerClass()));
    }

    boolean hasSomethingDirty = false;
    for (VcsRoot vcsRoot : ContainerUtil.union(filesConverted.keySet(), dirsConverted.keySet())) {
      Set<FilePath> files = ContainerUtil.notNullize(filesConverted.get(vcsRoot));
      Set<FilePath> dirs = ContainerUtil.notNullize(dirsConverted.get(vcsRoot));

      synchronized (LOCK) {
        if (myReady) {
          hasSomethingDirty |= myDirtBuilder.addDirtyFiles(vcsRoot, files, dirs);
        }
      }
    }

    if (hasSomethingDirty) {
      ChangeListManager.getInstance(myProject).scheduleUpdate();
    }
  }

  @Override
  public void filePathsDirty(@Nullable Collection<? extends FilePath> filesDirty,
                             @Nullable Collection<? extends FilePath> dirsRecursivelyDirty) {
    try {
      fileVcsPathsDirty(groupByVcs(filesDirty), groupByVcs(dirsRecursivelyDirty));
    }
    catch (ProcessCanceledException ignore) {
    }
  }

  @Override
  public void filesDirty(@Nullable Collection<? extends VirtualFile> filesDirty,
                         @Nullable Collection<? extends VirtualFile> dirsRecursivelyDirty) {
    try {
      fileVcsPathsDirty(groupFilesByVcs(filesDirty), groupFilesByVcs(dirsRecursivelyDirty));
    }
    catch (ProcessCanceledException ignore) {
    }
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

  /**
   * Take current dirty scope into processing.
   * Should call {@link #changesProcessed} when done to notify {@link #whatFilesDirty} that scope is no longer dirty.
   */
  @Nullable
  public VcsInvalidated retrieveScopes() {
    ActionCallback callback = new ActionCallback();
    DirtBuilder dirtBuilder;
    synchronized (LOCK) {
      if (!myReady) return null;
      LOG.assertTrue(myDirtInProgress == null);

      dirtBuilder = myDirtBuilder;
      myDirtInProgress = dirtBuilder;
      myDirtBuilder = new DirtBuilder();
      myRefreshInProgress = callback;
    }
    return calculateInvalidated(dirtBuilder, callback);
  }

  public void changesProcessed() {
    synchronized (LOCK) {
      myDirtInProgress = null;
      myRefreshInProgress = null;
    }
  }

  @NotNull
  private VcsInvalidated calculateInvalidated(@NotNull DirtBuilder dirt, @NotNull ActionCallback callback) {
    boolean isEverythingDirty = dirt.isEverythingDirty();
    List<VcsDirtyScopeImpl> scopes = dirt.buildScopes(myProject);
    return new VcsInvalidated(scopes, isEverythingDirty, callback);
  }

  @NotNull
  @Override
  public Collection<FilePath> whatFilesDirty(@NotNull final Collection<? extends FilePath> files) {
    return ReadAction.compute(() -> {
      Collection<FilePath> result = new ArrayList<>();
      synchronized (LOCK) {
        if (!myReady) return Collections.emptyList();

        for (FilePath fp : files) {
          if (myDirtBuilder.isFileDirty(fp) ||
              myDirtInProgress != null && myDirtInProgress.isFileDirty(fp)) {
            result.add(fp);
          }
        }
      }
      return result;
    });
  }

  @NotNull
  private static String toString(@NotNull Map<VcsRoot, Set<FilePath>> filesByVcs) {
    return StringUtil.join(filesByVcs.keySet(), vcs
      -> vcs.getVcs() + ": " + StringUtil.join(filesByVcs.get(vcs), path -> path.getPath(), "\n"), "\n");
  }

  @Nullable
  private static Class<?> findFirstInterestingCallerClass() {
    for (int i = 1; i <= 7; i++) {
      Class<?> clazz = ReflectionUtil.findCallerClass(i);
      if (clazz == null || !clazz.getName().contains(VcsDirtyScopeManagerImpl.class.getName())) return clazz;
    }
    return null;
  }

  @NotNull
  public static TObjectHashingStrategy<FilePath> getDirtyScopeHashingStrategy(@NotNull AbstractVcs vcs) {
    return vcs.needsCaseSensitiveDirtyScope() ? ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY
                                              : ContainerUtil.canonicalStrategy();
  }

  static final class MyStartupActivity implements VcsStartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      getInstanceImpl(project).startListenForChanges();
    }

    @Override
    public int getOrder() {
      return VcsInitObject.DIRTY_SCOPE_MANAGER.getOrder();
    }
  }
}
