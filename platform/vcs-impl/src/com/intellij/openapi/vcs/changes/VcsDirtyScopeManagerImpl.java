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
import com.intellij.util.Consumer;
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
  private final SynchronizedLife myLife;

  private final MyProgressHolder myProgressHolder;

  public VcsDirtyScopeManagerImpl(Project project, ChangeListManager changeListManager, ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myChangeListManager = changeListManager;
    myVcsManager = vcsManager;

    myLife = new SynchronizedLife();
    myGuess = new VcsGuess(myProject);
    myDirtBuilder = new DirtBuilder(myGuess);

    myProgressHolder = new MyProgressHolder();
    ((ChangeListManagerImpl) myChangeListManager).setDirtyScopeManager(this);
  }

  @Override
  public void projectOpened() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myLife.born();
      final AbstractVcs[] vcss = myVcsManager.getAllActiveVcss();
      if (vcss.length > 0) {
        markEverythingDirty();
      }
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
        @Override
        public void run() {
          myLife.born();
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

    boolean done = myLife.doIfAlive(new Runnable() {
      public void run() {
        myDirtBuilder.everythingDirty();
      }
    });

    if (done) {
      myChangeListManager.scheduleUpdate();
    }
  }

  @Override
  public void projectClosed() {
    killSelf();
  }

  @Override
  @NotNull @NonNls
  public String getComponentName() {
    return "VcsDirtyScopeManager";
  }

  @Override
  public void initComponent() {}

  private void killSelf() {
    myLife.kill(new Runnable() {
      @Override
      public void run() {
        myDirtBuilder.reset();
      }
    });
  }

  @Override
  public void disposeComponent() {
    killSelf();
  }

  @NotNull
  private Collection<FilePathUnderVcs> convertPaths(@Nullable final Collection<FilePath> from) {
    if (from == null) return Collections.emptyList();
    return ContainerUtil.mapNotNull(from, new Function<FilePath, FilePathUnderVcs>() {
      @Override
      public FilePathUnderVcs fun(FilePath path) {
        AbstractVcs vcs = myGuess.getVcsForDirty(path);
        return vcs == null ? null : new FilePathUnderVcs(path, vcs);
      }
    });
  }

  @Override
  public void filePathsDirty(@Nullable final Collection<FilePath> filesDirty, @Nullable final Collection<FilePath> dirsRecursivelyDirty) {
    try {
      final Collection<FilePathUnderVcs> filesConverted = convertPaths(filesDirty);
      final Collection<FilePathUnderVcs> dirsConverted = convertPaths(dirsRecursivelyDirty);
      if (filesConverted.isEmpty() && dirsConverted.isEmpty()) return;

      if (LOG.isDebugEnabled()) {
        LOG.debug("paths dirty: " + filesConverted + "; " + dirsConverted + "; " + ReflectionUtil.findCallerClass(3));
      }

      takeDirt(new Consumer<DirtBuilder>() {
        @Override
        public void consume(final DirtBuilder dirt) {
          for (FilePathUnderVcs root : filesConverted) {
            dirt.addDirtyFile(root);
          }
          for (FilePathUnderVcs root : dirsConverted) {
            dirt.addDirtyDirRecursively(root);
          }
        }
      });
    } catch (ProcessCanceledException ignore) {
    }
  }

  private void takeDirt(final Consumer<DirtBuilder> filler) {
    final Ref<Boolean> wasNotEmptyRef = new Ref<Boolean>();
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        filler.consume(myDirtBuilder);
        wasNotEmptyRef.set(!myDirtBuilder.isEmpty());
      }
    };
    boolean done = myLife.doIfAlive(runnable);

    if (done && Boolean.TRUE.equals(wasNotEmptyRef.get())) {
      myChangeListManager.scheduleUpdate();
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
    boolean done = myLife.doIfAlive(new Runnable() {
      public void run() {
        myProgressHolder.takeNext(new DirtBuilder(myDirtBuilder));
        myDirtBuilder.reset();
      }
    });

    if (done) {
      final VcsInvalidated invalidated = myProgressHolder.calculateInvalidated();

      myLife.doIfAlive(new Runnable() {
        @Override
        public void run() {
          myProgressHolder.takeInvalidated(invalidated);
        }
      });
      return invalidated;
    }
    return null;
  }

  @Override
  public void changesProcessed() {
    myLife.doIfAlive(new Runnable() {
      @Override
      public void run() {
        myProgressHolder.processed();
      }
    });
  }

  @NotNull
  @Override
  public Collection<FilePath> whatFilesDirty(@NotNull final Collection<FilePath> files) {
    final Collection<FilePath> result = new ArrayList<FilePath>();
    final Ref<MyProgressHolder> inProgressHolderRef = new Ref<MyProgressHolder>();
    final Ref<MyProgressHolder> currentHolderRef = new Ref<MyProgressHolder>();

    myLife.doIfAlive(new Runnable() {
      @Override
      public void run() {
        inProgressHolderRef.set(myProgressHolder.copy());
        currentHolderRef.set(new MyProgressHolder(new DirtBuilder(myDirtBuilder), null));
      }
    });
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

  private static class SynchronizedLife {
    private boolean myAlive;
    private final Object myLock = new Object();

    public void born() {
      synchronized (myLock) {
        myAlive = true;
      }
    }

    public void kill(Runnable runnable) {
      synchronized (myLock) {
        myAlive = false;
        runnable.run();
      }
    }

    // allow work under inner lock: inner class, not wide scope
    public boolean doIfAlive(final Runnable runnable) {
      synchronized (myLock) {
        if (myAlive) {
          runnable.run();
          return true;
        }
        return false;
      }
    }
  }
}
