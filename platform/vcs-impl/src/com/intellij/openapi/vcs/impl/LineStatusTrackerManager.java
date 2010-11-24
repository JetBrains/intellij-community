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
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.committed.AbstractCalledLater;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class LineStatusTrackerManager implements ProjectComponent, LineStatusTrackerManagerI {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.LineStatusTrackerManager");
  public final Object myLock = new Object();

  public static LineStatusTrackerManagerI getInstance(final Project project) {
    if (System.getProperty(IGNORE_CHANGEMARKERS_KEY) != null) {
      return Dummy.getInstance();
    }
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, LineStatusTrackerManagerI.class);
  }

  private final Project myProject;

  private final Map<Document, LineStatusTracker> myLineStatusTrackers;
  // !!! no state queries and self lock for add/remove
  // removal from here - not under write action
  private final Map<Document, Alarm> myLineStatusUpdateAlarms;

  @NonNls protected static final String IGNORE_CHANGEMARKERS_KEY = "idea.ignore.changemarkers";

  private final ProjectLevelVcsManagerImpl myVcsManager;
  private final VcsFileStatusProvider myStatusProvider;
  private final Application myApplication;
  private final FileEditorManager myFileEditorManager;
  private final Disposable myDisposable;

  public LineStatusTrackerManager(final Project project, final ProjectLevelVcsManagerImpl vcsManager, final VcsFileStatusProvider statusProvider,
                                  final Application application, final FileEditorManager fileEditorManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myStatusProvider = statusProvider;
    myApplication = application;
    myFileEditorManager = fileEditorManager;
    myLineStatusTrackers = new HashMap<Document, LineStatusTracker>();
    myLineStatusUpdateAlarms = Collections.synchronizedMap(new HashMap<Document, Alarm>());

    project.getMessageBus().connect().subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      public void updateStarted(final Document doc) {
        final LineStatusTracker tracker = getLineStatusTracker(doc);
        if (tracker != null) tracker.startBulkUpdate();
      }

      public void updateFinished(final Document doc) {
        final LineStatusTracker tracker = getLineStatusTracker(doc);
        if (tracker != null) tracker.finishBulkUpdate();
      }
    });

    myDisposable = new Disposable() {
      @Override
      public void dispose() {
        synchronized (myLock) {
          for (final LineStatusTracker tracker : myLineStatusTrackers.values()) {
            final Document document = tracker.getDocument();
            final Alarm alarm = myLineStatusUpdateAlarms.remove(document);
            if (alarm != null) {
              alarm.cancelAllRequests();
            }
            tracker.release();
          }

          myLineStatusTrackers.clear();
          assert myLineStatusUpdateAlarms.isEmpty();
          myLineStatusUpdateAlarms.clear();
        }
      }
    };
    Disposer.register(myProject, myDisposable);
  }

  public void projectOpened() {
    final MyFileStatusListener fileStatusListener = new MyFileStatusListener();
    final EditorFactoryListener editorFactoryListener = new MyEditorFactoryListener();
    final MyVirtualFileListener virtualFileListener = new MyVirtualFileListener();
    final EditorColorsListener editorColorsListener = new EditorColorsListener() {
      public void globalSchemeChange(EditorColorsScheme scheme) {
        resetTrackersForOpenFiles();
      }
    };

    final FileStatusManager fsManager = FileStatusManager.getInstance(myProject);
    fsManager.addFileStatusListener(fileStatusListener, myProject);

    final EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.addEditorFactoryListener(editorFactoryListener,myProject);

    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    virtualFileManager.addVirtualFileListener(virtualFileListener,myProject);

    final EditorColorsManager editorColorsManager = EditorColorsManager.getInstance();
    editorColorsManager.addEditorColorsListener(editorColorsListener);

    Disposer.register(myDisposable, new Disposable() {
      public void dispose() {
        fsManager.removeFileStatusListener(fileStatusListener);
        virtualFileManager.removeVirtualFileListener(virtualFileListener);
        editorColorsManager.removeEditorColorsListener(editorColorsListener);
      }
    });
  }

  public void projectClosed() {
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "LineStatusTrackerManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @Override
  public LineStatusTracker getLineStatusTracker(final Document document) {
    myApplication.assertReadAccessAllowed();
    if ((! myProject.isOpen()) || myProject.isDisposed()) return null;

    synchronized (myLock) {
      return myLineStatusTrackers.get(document);
    }
  }

  private void resetTracker(@NotNull final VirtualFile virtualFile) {
    myApplication.assertReadAccessAllowed();
    if ((! myProject.isOpen()) || myProject.isDisposed()) return;

    final Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
    if (document == null) {
      log("Skipping resetTracker() because no cached document for " + virtualFile.getPath());
      return;
    }

    log("resetting tracker for file " + virtualFile.getPath());

    final boolean editorOpened = myFileEditorManager.isFileOpen(virtualFile);
    final boolean shouldBeInstalled = shouldBeInstalled(virtualFile) && editorOpened;

    synchronized (myLock) {
      final LineStatusTracker tracker = myLineStatusTrackers.get(document);

      if (tracker == null && (! shouldBeInstalled)) return;

      if (tracker != null) {
        if (! shouldBeInstalled) {
          releaseTracker(document);
          return;
        } else if ((LineStatusTracker.BaseLoadState.LOADING == tracker.getBaseLoaded())) {
          return; // will be recalculated
        } else {
          tracker.resetForBaseRevisionLoad();
          startAlarm(document, virtualFile);
        }
      } else if (shouldBeInstalled) {
        installTracker(virtualFile, document);
      }
    }
  }

  private void releaseTracker(final Document document) {
    if ((! myProject.isOpen()) || myProject.isDisposed()) return;

    synchronized (myLock) {
      final Alarm alarm = myLineStatusUpdateAlarms.remove(document);
      if (alarm != null) {
        alarm.cancelAllRequests();
      }
      final LineStatusTracker tracker = myLineStatusTrackers.remove(document);
      if (tracker != null) {
        tracker.release();
      }
    }
  }

  private boolean shouldBeInstalled(final VirtualFile virtualFile) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (virtualFile == null || virtualFile instanceof LightVirtualFile) return false;
    if (! virtualFile.isInLocalFileSystem()) return false;
    if ((! myProject.isOpen()) || myProject.isDisposed()) return false;
    final FileStatusManager statusManager = FileStatusManager.getInstance(myProject);
    if (statusManager == null) return false;
    final AbstractVcs activeVcs = myVcsManager.getVcsFor(virtualFile);
    if (activeVcs == null) {
      log("installTracker() for file " + virtualFile.getPath() + " failed: no active VCS");
      return false;
    }
    final FileStatus status = statusManager.getStatus(virtualFile);
    if (status == FileStatus.NOT_CHANGED || status == FileStatus.ADDED || status == FileStatus.UNKNOWN || status == FileStatus.IGNORED) {
      log("installTracker() for file " + virtualFile.getPath() + " failed: status=" + status);
      return false;
    }
    return true;
  }

  private void installTracker(final VirtualFile virtualFile, final Document document) {
    synchronized (myLock) {
      if (myLineStatusTrackers.containsKey(document)) return;
      assert !myLineStatusUpdateAlarms.containsKey(document);

      final LineStatusTracker tracker = LineStatusTracker.createOn(document, myProject);
      myLineStatusTrackers.put(document, tracker);

      startAlarm(document, virtualFile);
    }
  }

  private void startAlarm(final Document document, final VirtualFile virtualFile) {
    myApplication.assertReadAccessAllowed();

    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    synchronized (myLock) {
      myLineStatusUpdateAlarms.put(document, alarm);
    }
    alarm.addRequest(new BaseRevisionLoader(alarm, document, virtualFile), 10);
  }

  private class BaseRevisionLoader implements Runnable {
    private final Alarm myAlarm;
    private final VirtualFile myVirtualFile;
    private final Document myDocument;

    private BaseRevisionLoader(final Alarm alarm, final Document document, final VirtualFile virtualFile) {
      myAlarm = alarm;
      myDocument = document;
      myVirtualFile = virtualFile;
    }

    @Override
    public void run() {
      myAlarm.cancelAllRequests();
      synchronized (myLock) {
        final Alarm removed = myLineStatusUpdateAlarms.remove(myDocument);
        if (removed == null) {
          return;
        }
      }
      if ((! myProject.isOpen()) || myProject.isDisposed()) return;

      if (! myVirtualFile.isValid()) {
        log("installTracker() for file " + myVirtualFile.getPath() + " failed: virtual file not valid");
        reportTrackerBaseLoadFailed();
        return;
      }

      final String lastUpToDateContent = myStatusProvider.getBaseVersionContent(myVirtualFile);
      if (lastUpToDateContent == null) {
        log("installTracker() for file " + myVirtualFile.getPath() + " failed: no up to date content");
        reportTrackerBaseLoadFailed();
        return;
      }

      final String converted = StringUtil.convertLineSeparators(lastUpToDateContent);
      myApplication.invokeLater(new Runnable() {
        public void run() {
          synchronized (myLock) {
            log("initializing tracker for file " + myVirtualFile.getPath());
            final LineStatusTracker tracker = myLineStatusTrackers.get(myDocument);
            if (tracker != null) {
              tracker.initialize(converted);
            }
          }
        }
      }, new Condition() {
        @Override
        public boolean value(final Object ignore) {
          return (! myProject.isOpen()) || myProject.isDisposed();
        }
      });
    }

    private void reportTrackerBaseLoadFailed() {
      synchronized (myLock) {
        log("base revision load failed for file " + myVirtualFile.getPath());
        final LineStatusTracker tracker = myLineStatusTrackers.get(myDocument);
        if (tracker != null) {
          tracker.baseRevisionLoadFailed();
        }
      }
    }
  }

  private void resetTrackersForOpenFiles() {
    myApplication.assertReadAccessAllowed();
    if ((! myProject.isOpen()) || myProject.isDisposed()) return;

    final VirtualFile[] openFiles = myFileEditorManager.getOpenFiles();
    for(final VirtualFile openFile: openFiles) {
      resetTracker(openFile);
    }
  }

  private class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      if (myProject.isDisposed()) return;
      log("LineStatusTrackerManager: fileStatusesChanged");
      resetTrackersForOpenFiles();
    }

    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      resetTracker(virtualFile);
    }
  }

  private class MyEditorFactoryListener extends EditorFactoryAdapter {
    public void editorCreated(EditorFactoryEvent event) {
      // note that in case of lazy loading of configurables, this event can happen
      // outside of EDT, so the EDT check mustn't be done here
      Editor editor = event.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      final Document document = editor.getDocument();
      final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);

      new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
        @Override
        public void run() {
          if (shouldBeInstalled(virtualFile)) {
            installTracker(virtualFile, document);
          }
        }
      }.callMe();
    }

    public void editorReleased(EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      final Document doc = editor.getDocument();
      final Editor[] editors = event.getFactory().getEditors(doc, myProject);
      if (editors.length == 0) {
        new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
          @Override
          public void run() {
            releaseTracker(doc);
          }
        }.callMe();
      }
    }
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    public void beforeContentsChange(VirtualFileEvent event) {
      if (event.isFromRefresh()) {
        resetTracker(event.getFile());
      }
    }
  }

  private static void log(final String s) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(s);
    }
  }
}
