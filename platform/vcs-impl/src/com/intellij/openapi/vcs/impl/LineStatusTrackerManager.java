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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.07.2006
 * Time: 13:24:17
 */
package com.intellij.openapi.vcs.impl;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessorRemovePartner;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class LineStatusTrackerManager implements ProjectComponent, LineStatusTrackerManagerI {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.LineStatusTrackerManager");

  @NonNls protected static final String IGNORE_CHANGEMARKERS_KEY = "idea.ignore.changemarkers";

  @NotNull public final Object myLock = new Object();

  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final VcsBaseContentProvider myStatusProvider;
  @NotNull private final Application myApplication;
  @NotNull private final FileEditorManager myFileEditorManager;
  @NotNull private final Disposable myDisposable;

  @NotNull private final Map<Document, LineStatusTracker> myLineStatusTrackers;

  @NotNull private final QueueProcessorRemovePartner<Document, BaseRevisionLoader> myPartner;
  private long myLoadCounter;

  public static LineStatusTrackerManagerI getInstance(final Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, LineStatusTrackerManagerI.class);
  }

  public LineStatusTrackerManager(@NotNull final Project project,
                                  @NotNull final ProjectLevelVcsManager vcsManager,
                                  @NotNull final VcsBaseContentProvider statusProvider,
                                  @NotNull final Application application,
                                  @NotNull final FileEditorManager fileEditorManager,
                                  @SuppressWarnings("UnusedParameters") DirectoryIndex makeSureIndexIsInitializedFirst) {
    myLoadCounter = 0;
    myProject = project;
    myVcsManager = vcsManager;
    myStatusProvider = statusProvider;
    myApplication = application;
    myFileEditorManager = fileEditorManager;

    myLineStatusTrackers = new HashMap<Document, LineStatusTracker>();
    myPartner = new QueueProcessorRemovePartner<Document, BaseRevisionLoader>(myProject, new Consumer<BaseRevisionLoader>() {
      @Override
      public void consume(BaseRevisionLoader baseRevisionLoader) {
        baseRevisionLoader.run();
      }
    });

    MessageBusConnection busConnection = project.getMessageBus().connect();
    busConnection.subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      @Override
      public void updateStarted(@NotNull final Document doc) {
        final LineStatusTracker tracker = getLineStatusTracker(doc);
        if (tracker != null) tracker.startBulkUpdate();
      }

      @Override
      public void updateFinished(@NotNull final Document doc) {
        final LineStatusTracker tracker = getLineStatusTracker(doc);
        if (tracker != null) tracker.finishBulkUpdate();
      }
    });
    busConnection.subscribe(LineStatusTrackerSettingListener.TOPIC, new LineStatusTrackerSettingListener() {
      @Override
      public void settingsUpdated() {
        synchronized (myLock) {
          LineStatusTracker.Mode mode = getMode();
          for (LineStatusTracker tracker : myLineStatusTrackers.values()) {
            tracker.setMode(mode);
          }
        }
      }
    });

    myDisposable = new Disposable() {
      @Override
      public void dispose() {
        synchronized (myLock) {
          for (final LineStatusTracker tracker : myLineStatusTrackers.values()) {
            tracker.release();
          }

          myLineStatusTrackers.clear();
          myPartner.clear();
        }
      }
    };
    Disposer.register(myProject, myDisposable);
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPreStartupActivity(new Runnable() {
      @Override
      public void run() {
        final MyFileStatusListener fileStatusListener = new MyFileStatusListener();
        final EditorFactoryListener editorFactoryListener = new MyEditorFactoryListener();
        final MyVirtualFileListener virtualFileListener = new MyVirtualFileListener();
        final EditorColorsListener editorColorsListener = new MyEditorColorsListener();

        final FileStatusManager fsManager = FileStatusManager.getInstance(myProject);
        fsManager.addFileStatusListener(fileStatusListener, myDisposable);

        final EditorFactory editorFactory = EditorFactory.getInstance();
        editorFactory.addEditorFactoryListener(editorFactoryListener, myDisposable);

        final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
        virtualFileManager.addVirtualFileListener(virtualFileListener, myDisposable);

        final EditorColorsManager editorColorsManager = EditorColorsManager.getInstance();
        editorColorsManager.addEditorColorsListener(editorColorsListener, myDisposable);
      }
    });
  }

  @Override
  public void projectClosed() {
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "LineStatusTrackerManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  public boolean isDisabled() {
    return !myProject.isOpen() || myProject.isDisposed();
  }

  @Override
  public LineStatusTracker getLineStatusTracker(final Document document) {
    synchronized (myLock) {
      if (isDisabled()) return null;

      return myLineStatusTrackers.get(document);
    }
  }

  private void resetTrackers() {
    synchronized (myLock) {
      if (isDisabled()) return;

      if (LOG.isDebugEnabled()) {
        LOG.debug("resetTrackers");
      }

      for (LineStatusTracker tracker : ContainerUtil.newArrayList(myLineStatusTrackers.values())) {
        resetTracker(tracker.getDocument(), tracker.getVirtualFile(), tracker);
      }

      final VirtualFile[] openFiles = myFileEditorManager.getOpenFiles();
      for (final VirtualFile openFile : openFiles) {
        resetTracker(openFile, true);
      }
    }
  }

  private void resetTracker(@NotNull final VirtualFile virtualFile) {
    resetTracker(virtualFile, false);
  }

  private void resetTracker(@NotNull final VirtualFile virtualFile, boolean insertOnly) {
    final Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
    if (document == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("resetTracker: no cached document for " + virtualFile.getPath());
      }
      return;
    }

    synchronized (myLock) {
      if (isDisabled()) return;

      final LineStatusTracker tracker = myLineStatusTrackers.get(document);
      if (insertOnly && tracker != null) return;
      resetTracker(document, virtualFile, tracker);
    }
  }

  private void resetTracker(@NotNull Document document, @NotNull VirtualFile virtualFile, @Nullable LineStatusTracker tracker) {
    final boolean editorOpened = myFileEditorManager.isFileOpen(virtualFile);
    final boolean shouldBeInstalled = editorOpened && shouldBeInstalled(virtualFile);

    if (LOG.isDebugEnabled()) {
      LOG.debug("resetTracker: shouldBeInstalled - " + shouldBeInstalled + ", tracker - " + (tracker == null ? "null" : "found"));
    }

    if (tracker != null && shouldBeInstalled) {
      refreshTracker(tracker);
    }
    else if (tracker != null) {
      releaseTracker(document);
    }
    else if (shouldBeInstalled) {
      installTracker(virtualFile, document);
    }
  }

  private boolean shouldBeInstalled(@Nullable final VirtualFile virtualFile) {
    if (isDisabled()) return false;

    if (virtualFile == null || virtualFile instanceof LightVirtualFile) return false;
    if (!virtualFile.isInLocalFileSystem()) return false;
    final FileStatusManager statusManager = FileStatusManager.getInstance(myProject);
    if (statusManager == null) return false;
    final AbstractVcs activeVcs = myVcsManager.getVcsFor(virtualFile);
    if (activeVcs == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("shouldBeInstalled: for file " + virtualFile.getPath() + " failed: no active VCS");
      }
      return false;
    }
    final FileStatus status = statusManager.getStatus(virtualFile);
    if (status == FileStatus.NOT_CHANGED || status == FileStatus.ADDED || status == FileStatus.UNKNOWN || status == FileStatus.IGNORED) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("shouldBeInstalled: for file " + virtualFile.getPath() + " skipped: status=" + status);
      }
      return false;
    }
    return true;
  }

  private void refreshTracker(@NotNull LineStatusTracker tracker) {
    synchronized (myLock) {
      if (isDisabled()) return;

      startAlarm(tracker.getDocument(), tracker.getVirtualFile());
    }
  }

  private void releaseTracker(@NotNull final Document document) {
    synchronized (myLock) {
      if (isDisabled()) return;

      myPartner.remove(document);
      final LineStatusTracker tracker = myLineStatusTrackers.remove(document);
      if (tracker != null) {
        tracker.release();
      }
    }
  }

  private void installTracker(@NotNull final VirtualFile virtualFile, @NotNull final Document document) {
    synchronized (myLock) {
      if (isDisabled()) return;

      if (myLineStatusTrackers.containsKey(document)) return;
      assert !myPartner.containsKey(document);

      final LineStatusTracker tracker = LineStatusTracker.createOn(virtualFile, document, myProject, getMode());
      myLineStatusTrackers.put(document, tracker);

      startAlarm(document, virtualFile);
    }
  }

  @NotNull
  private static LineStatusTracker.Mode getMode() {
    VcsApplicationSettings vcsApplicationSettings = VcsApplicationSettings.getInstance();
    if (!vcsApplicationSettings.SHOW_LST_GUTTER_MARKERS) return LineStatusTracker.Mode.SILENT;
    return vcsApplicationSettings.SHOW_WHITESPACES_IN_LST ? LineStatusTracker.Mode.SMART : LineStatusTracker.Mode.DEFAULT;
  }

  private void startAlarm(@NotNull final Document document, @NotNull final VirtualFile virtualFile) {
    synchronized (myLock) {
      myPartner.add(document, new BaseRevisionLoader(document, virtualFile));
    }
  }

  private class BaseRevisionLoader implements Runnable {
    @NotNull private final VirtualFile myVirtualFile;
    @NotNull private final Document myDocument;

    private BaseRevisionLoader(@NotNull final Document document, @NotNull final VirtualFile virtualFile) {
      myDocument = document;
      myVirtualFile = virtualFile;
    }

    @Override
    public void run() {
      if (isDisabled()) return;

      if (!myVirtualFile.isValid()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("BaseRevisionLoader: for file " + myVirtualFile.getPath() + " failed: virtual file not valid");
        }
        reportTrackerBaseLoadFailed();
        return;
      }

      final Pair<VcsRevisionNumber, String> baseRevision = myStatusProvider.getBaseRevision(myVirtualFile);
      if (baseRevision == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("BaseRevisionLoader: for file " + myVirtualFile.getPath() + " failed: null returned for base revision");
        }
        reportTrackerBaseLoadFailed();
        return;
      }

      // loads are sequential (in single threaded QueueProcessor);
      // so myLoadCounter can't take less value for greater base revision -> the only thing we want from it
      final LineStatusTracker.RevisionPack revisionPack = new LineStatusTracker.RevisionPack(myLoadCounter, baseRevision.first);
      myLoadCounter++;

      final String converted = StringUtil.convertLineSeparators(baseRevision.second);
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          synchronized (myLock) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("BaseRevisionLoader: initializing tracker for file " + myVirtualFile.getPath());
            }
            final LineStatusTracker tracker = myLineStatusTrackers.get(myDocument);
            if (tracker != null) {
              tracker.initialize(converted, revisionPack);
            }
          }
        }
      };
      nonModalAliveInvokeLater(runnable);
    }

    private void nonModalAliveInvokeLater(@NotNull Runnable runnable) {
      myApplication.invokeLater(runnable, ModalityState.NON_MODAL, new Condition() {
        @Override
        public boolean value(final Object ignore) {
          return isDisabled();
        }
      });
    }

    private void reportTrackerBaseLoadFailed() {
      synchronized (myLock) {
        releaseTracker(myDocument);
      }
    }
  }

  private class MyFileStatusListener implements FileStatusListener {
    @Override
    public void fileStatusesChanged() {
      resetTrackers();
    }

    @Override
    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      resetTracker(virtualFile);
    }
  }

  private class MyEditorFactoryListener extends EditorFactoryAdapter {
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
      // note that in case of lazy loading of configurables, this event can happen
      // outside of EDT, so the EDT check mustn't be done here
      Editor editor = event.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      final Document document = editor.getDocument();
      final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile == null) return;
      if (shouldBeInstalled(virtualFile)) {
        installTracker(virtualFile, document);
      }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      final Document doc = editor.getDocument();
      final Editor[] editors = event.getFactory().getEditors(doc, myProject);
      if (editors.length == 0) {
        releaseTracker(doc);
      }
    }
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    @Override
    public void beforeContentsChange(@NotNull VirtualFileEvent event) {
      if (event.isFromRefresh()) {
        resetTracker(event.getFile());
      }
    }
  }

  private class MyEditorColorsListener implements EditorColorsListener {
    @Override
    public void globalSchemeChange(EditorColorsScheme scheme) {
      resetTrackers();
    }
  }
}
