// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents;

public class VcsAnnotationLocalChangesListenerImpl implements Disposable, VcsAnnotationLocalChangesListener {
  private static final Logger LOG = Logger.getInstance(VcsAnnotationLocalChangesListenerImpl.class);

  private final ZipperUpdater myUpdater;
  private final MessageBusConnection myConnection;

  private final Runnable myUpdateStuff;

  private final Set<String> myDirtyPaths;
  private final Set<VirtualFile> myDirtyFiles;
  private final Map<String, VcsRevisionNumber> myDirtyChanges;
  private final LocalFileSystem myLocalFileSystem;
  private final ProjectLevelVcsManager myVcsManager;
  private final Set<VcsKey> myVcsKeySet;
  private final Object myLock;

  private final MultiMap<VirtualFile, FileAnnotation> myFileAnnotationMap;

  public VcsAnnotationLocalChangesListenerImpl(@NotNull Project project) {
    myLock = new Object();
    myUpdateStuff = createUpdateStuff();
    myUpdater = new ZipperUpdater(getApplication().isUnitTestMode() ? 10 : 300, Alarm.ThreadToUse.POOLED_THREAD, project);
    myConnection = project.getMessageBus().connect();
    myLocalFileSystem = LocalFileSystem.getInstance();
    VcsAnnotationRefresher handler = createHandler();
    myDirtyPaths = new HashSet<>();
    myDirtyChanges = new HashMap<>();
    myDirtyFiles = new HashSet<>();
    myFileAnnotationMap = MultiMap.createSet();
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myVcsKeySet = new HashSet<>();

    myConnection.subscribe(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED, handler);
  }

  @TestOnly
  public void calmDown() {
    myUpdater.waitForAllExecuted(10, TimeUnit.SECONDS);
    // wait for FileAnnotation.close()/reload() to be called - see invalidateAnnotations()
    if (getApplication().isDispatchThread()) {
      dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
  }

  private Runnable createUpdateStuff() {
    return () -> {
      final Set<String> paths;
      final Map<String, VcsRevisionNumber> changes;
      final Set<VirtualFile> files;
      Set<VcsKey> vcsToRefresh;
      synchronized (myLock) {
        vcsToRefresh = new HashSet<>(myVcsKeySet);

        paths = new HashSet<>(myDirtyPaths);
        changes = new HashMap<>(myDirtyChanges);
        files = new HashSet<>(myDirtyFiles);
        myDirtyPaths.clear();
        myDirtyChanges.clear();
        myVcsKeySet.clear();
        myDirtyFiles.clear();
      }

      closeForVcs(vcsToRefresh);
      checkByDirtyScope(paths, changes, files);
    };
  }

  private void checkByDirtyScope(Set<String> removed, Map<String, VcsRevisionNumber> refresh, Set<? extends VirtualFile> files) {
    for (String path : removed) {
      refreshForPath(path, null);
    }
    for (Map.Entry<String, VcsRevisionNumber> entry : refresh.entrySet()) {
      refreshForPath(entry.getKey(), entry.getValue());
    }
    for (VirtualFile file : files) {
      processUnderFile(file);
    }
  }

  private void processUnderFile(VirtualFile file) {
    final MultiMap<VirtualFile, FileAnnotation> annotations = new MultiMap<>();
    synchronized (myLock) {
      for (VirtualFile virtualFile : myFileAnnotationMap.keySet()) {
        if (VfsUtilCore.isAncestor(file, virtualFile, true)) {
          final Collection<FileAnnotation> values = myFileAnnotationMap.get(virtualFile);
          for (FileAnnotation value : values) {
            annotations.putValue(virtualFile, value);
          }
        }
      }
    }
    if (! annotations.isEmpty()) {
      for (Map.Entry<VirtualFile, Collection<FileAnnotation>> entry : annotations.entrySet()) {
        final VirtualFile key = entry.getKey();
        final VcsRevisionNumber number = fromDiffProvider(key);
        if (number == null) continue;
        final Collection<FileAnnotation> fileAnnotations = entry.getValue();
        List<FileAnnotation> copy = ContainerUtil.filter(fileAnnotations, it -> it.isBaseRevisionChanged(number));
        invalidateAnnotations(copy, false);
      }
    }
  }

  private void refreshForPath(String path, VcsRevisionNumber number) {
    final File file = new File(path);
    VirtualFile vf = myLocalFileSystem.findFileByIoFile(file);
    if (vf == null) {
      vf = myLocalFileSystem.refreshAndFindFileByIoFile(file);
    }
    if (vf == null) return;
    processFile(number, vf);
  }

  private void processFile(VcsRevisionNumber number, VirtualFile vf) {
    final Collection<FileAnnotation> annotations;
    synchronized (myLock) {
      annotations = new ArrayList<>(myFileAnnotationMap.get(vf));
    }
    if (! annotations.isEmpty()) {
      if (number == null) {
        number = fromDiffProvider(vf);
      }
      if (number == null) return;

      VcsRevisionNumber finalNumber = number;
      List<FileAnnotation> copy = ContainerUtil.filter(annotations, it -> it.isBaseRevisionChanged(finalNumber));
      invalidateAnnotations(copy, false);
    }
  }

  private VcsRevisionNumber fromDiffProvider(final VirtualFile vf) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(vf);
    DiffProvider diffProvider;
    if (vcsRoot != null && vcsRoot.getVcs() != null && (diffProvider = vcsRoot.getVcs().getDiffProvider()) != null) {
      return diffProvider.getCurrentRevision(vf);
    }
    return null;
  }

  private void closeForVcs(final Set<VcsKey> refresh) {
    if (refresh.isEmpty()) return;
    synchronized (myLock) {
      List<FileAnnotation> copy = ContainerUtil.filter(myFileAnnotationMap.values(), it -> it.getVcsKey() != null && refresh.contains(it.getVcsKey()));
      invalidateAnnotations(copy, false);
    }
  }

  private static void invalidateAnnotations(@NotNull Collection<? extends FileAnnotation> annotations, boolean reload) {
    getApplication().invokeLater(() -> {
      for (FileAnnotation annotation: annotations) {
        try {
          if (reload) {
            annotation.reload(null);
          }
          else {
            annotation.close();
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  // annotations for already committed revisions should not register with this method - they are not subject to refresh
  @Override
  public void registerAnnotation(final VirtualFile file, final FileAnnotation annotation) {
    synchronized (myLock) {
      myFileAnnotationMap.putValue(file, annotation);
    }
  }

  @Override
  public void unregisterAnnotation(final VirtualFile file, final FileAnnotation annotation) {
    synchronized (myLock) {
      final Collection<FileAnnotation> annotations = myFileAnnotationMap.get(file);
      if (!annotations.isEmpty()) {
        annotations.remove(annotation);
      }
      if (annotations.isEmpty()) {
        myFileAnnotationMap.remove(file);
      }
    }
  }

  @Override
  public void reloadAnnotationsForVcs(@NotNull VcsKey key) {
    synchronized (myLock) {
      List<FileAnnotation> copy = ContainerUtil.filter(myFileAnnotationMap.values(), it -> key.equals(it.getVcsKey()));
      invalidateAnnotations(copy, true);
    }
  }

  @Override
  public void dispose() {
    myConnection.disconnect();
    myUpdater.stop();
  }

  private VcsAnnotationRefresher createHandler() {
    return new VcsAnnotationRefresher() {
      @Override
      public void dirtyUnder(VirtualFile file) {
        if (file == null) return;
        synchronized (myLock) {
          myDirtyFiles.add(file);
        }
        myUpdater.queue(myUpdateStuff);
      }

      @Override
      public void dirty(BaseRevision currentRevision) {
        synchronized (myLock) {
          myDirtyChanges.put(currentRevision.getPath(), currentRevision.getRevision());
        }
        myUpdater.queue(myUpdateStuff);
      }

      @Override
      public void dirty(String path) {
        synchronized (myLock) {
          myDirtyPaths.add(path);
        }
        myUpdater.queue(myUpdateStuff);
      }

      @Override
      public void configurationChanged(VcsKey vcsKey) {
        synchronized (myLock) {
          myVcsKeySet.add(vcsKey);
        }
        myUpdater.queue(myUpdateStuff);
      }
    };
  }
}
