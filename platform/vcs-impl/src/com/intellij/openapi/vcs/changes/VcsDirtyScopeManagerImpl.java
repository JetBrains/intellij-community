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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author max
 */
public class VcsDirtyScopeManagerImpl extends VcsDirtyScopeManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl");

  private final Project myProject;
  private final ChangeListManager myChangeListManager;
  private final ProjectLevelVcsManager myVcsManager;

  private final DirtBuilder myDirtBuilder;
  private final VcsGuess myGuess;

  private final MyProgressHolder myProgressHolder;

  private boolean myDisposed;
  private final Object LOCK = new Object();

  public VcsDirtyScopeManagerImpl(Project project, ChangeListManager changeListManager, ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myChangeListManager = changeListManager;
    myVcsManager = vcsManager;

    myGuess = new VcsGuess(myProject);
    myDirtBuilder = new DirtBuilder(myGuess);

    myProgressHolder = new MyProgressHolder();
    ((ChangeListManagerImpl) myChangeListManager).setDirtyScopeManager(this);
  }

  @Override
  public void projectOpened() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final AbstractVcs[] vcss = myVcsManager.getAllActiveVcss();
      if (vcss.length > 0) {
        markEverythingDirty();
      }
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              markEverythingDirty();
            }
          });
        }
      });
    }
  }

  public void markEverythingDirty() {
    if ((! myProject.isOpen()) || myProject.isDisposed() || myVcsManager.getAllActiveVcss().length == 0) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("everything dirty: " + ReflectionUtil.findCallerClass(2));
    }

    synchronized (LOCK) {
      if (!myDisposed) {
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
      myDisposed = true;
      myDirtBuilder.reset();
    }
  }

  @NotNull
  private MultiMap<AbstractVcs, FilePath> convertPaths(@Nullable final Collection<FilePath> from) {
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
      final MultiMap<AbstractVcs, FilePath> filesConverted = convertPaths(filesDirty);
      final MultiMap<AbstractVcs, FilePath> dirsConverted = convertPaths(dirsRecursivelyDirty);
      if (filesConverted.isEmpty() && dirsConverted.isEmpty()) return;

      if (LOG.isDebugEnabled()) {
        LOG.debug("paths dirty: " + filesConverted + "; " + dirsConverted + "; " + ReflectionUtil.findCallerClass(3));
      }

      boolean hasSomethingDirty;
      synchronized (LOCK) {
        if (myDisposed) return;
        for (AbstractVcs vcs : filesConverted.keySet()) {
          for (FilePath path : filesConverted.get(vcs)) {
            myDirtBuilder.addDirtyFile(vcs, path);
          }
        }
        for (AbstractVcs vcs : dirsConverted.keySet()) {
          for (FilePath path : dirsConverted.get(vcs)) {
            myDirtBuilder.addDirtyDirRecursively(vcs, path);
          }
        }
        hasSomethingDirty = !myDirtBuilder.isEmpty();
      }

      if (hasSomethingDirty) {
        myChangeListManager.scheduleUpdate();
      }
    }
    catch (ProcessCanceledException ignore) {
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

  private class MyProgressHolder {
    private VcsInvalidated myInProgressState;
    private DirtBuilderReader myInProgressDirtBuilder;

    public MyProgressHolder() {
      myInProgressDirtBuilder = new DirtBuilder(myGuess);
      myInProgressState = null;
    }

    public void takeNext(final DirtBuilderReader dirtBuilder) {
      myInProgressDirtBuilder = dirtBuilder;
      myInProgressState = null;
    }

    private MyProgressHolder(final DirtBuilderReader dirtBuilder, final VcsInvalidated vcsInvalidated) {
      myInProgressDirtBuilder = dirtBuilder;
      myInProgressState = vcsInvalidated;
    }

    public VcsInvalidated calculateInvalidated() {
      if (myInProgressDirtBuilder != null) {
        final Scopes scopes = new Scopes(myProject, myGuess);
        scopes.takeDirt(myInProgressDirtBuilder);
        return scopes.retrieveAndClear();
      }
      return myInProgressState;
    }

    public void takeInvalidated(final VcsInvalidated invalidated) {
      myInProgressState = invalidated;
      myInProgressDirtBuilder = null;
    }

    public void processed() {
      myInProgressState = null;
      myInProgressDirtBuilder = null;
    }

    public MyProgressHolder copy() {
      return new MyProgressHolder(myInProgressDirtBuilder, myInProgressState);
    }
  }

  @Override
  @Nullable
  public VcsInvalidated retrieveScopes() {
    synchronized (LOCK) {
      if (myDisposed) return null;
      myProgressHolder.takeNext(new DirtBuilder(myDirtBuilder));
      myDirtBuilder.reset();
    }

    VcsInvalidated invalidated = myProgressHolder.calculateInvalidated();
    synchronized (LOCK) {
      if (!myDisposed) {
        myProgressHolder.takeInvalidated(invalidated);
      }
    }
    return invalidated;
  }

  @Override
  public void changesProcessed() {
    synchronized (LOCK) {
      if (!myDisposed) {
        myProgressHolder.processed();
      }
    }
  }

  @NotNull
  @Override
  public Collection<FilePath> whatFilesDirty(@NotNull final Collection<FilePath> files) {
    final Collection<FilePath> result = new ArrayList<FilePath>();
    final Ref<MyProgressHolder> inProgressHolderRef = new Ref<MyProgressHolder>();
    final Ref<MyProgressHolder> currentHolderRef = new Ref<MyProgressHolder>();

    synchronized (LOCK) {
      if (!myDisposed) {
        inProgressHolderRef.set(myProgressHolder.copy());
        currentHolderRef.set(new MyProgressHolder(new DirtBuilder(myDirtBuilder), null));
      }
    }

    final VcsInvalidated inProgressInvalidated = inProgressHolderRef.get() == null ? null : inProgressHolderRef.get().calculateInvalidated();
    final VcsInvalidated currentInvalidated = currentHolderRef.get() == null ? null : currentHolderRef.get().calculateInvalidated();
    for (FilePath fp : files) {
      if (inProgressInvalidated != null && inProgressInvalidated.isFileDirty(fp)
          || currentInvalidated != null && currentInvalidated.isFileDirty(fp)) {
        result.add(fp);
      }
    }
    return result;
  }
}
