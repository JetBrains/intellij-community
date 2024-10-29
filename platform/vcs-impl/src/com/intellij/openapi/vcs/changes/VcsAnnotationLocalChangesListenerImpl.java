// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
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
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents;

@ApiStatus.Internal
public class VcsAnnotationLocalChangesListenerImpl implements Disposable, VcsAnnotationLocalChangesListener {
  private static final Logger LOG = Logger.getInstance(VcsAnnotationLocalChangesListenerImpl.class);

  private final ZipperUpdater myUpdater;
  private final LocalFileSystem myLocalFileSystem;
  private final ProjectLevelVcsManager myVcsManager;

  private final Set<String> myDirtyPaths = new HashSet<>();
  private final Set<VirtualFile> myDirtyFiles = new HashSet<>();
  private final Map<String, VcsRevisionNumber> myDirtyChanges = new HashMap<>();
  private final Set<VcsKey> myVcsKeySet = new HashSet<>();
  private final Object myLock = new Object();

  private final List<FileAnnotation> myFileAnnotations = new ArrayList<>();

  public VcsAnnotationLocalChangesListenerImpl(@NotNull Project project) {
    myUpdater = new ZipperUpdater(getApplication().isUnitTestMode() ? 10 : 300, Alarm.ThreadToUse.POOLED_THREAD, this);
    myLocalFileSystem = LocalFileSystem.getInstance();
    myVcsManager = ProjectLevelVcsManager.getInstance(project);

    MessageBusConnection busConnection = project.getMessageBus().connect(this);
    busConnection.subscribe(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED, new MyRefresher());

    MessageBusConnection appConnection = getApplication().getMessageBus().connect(this);
    appConnection.subscribe(EditorColorsManager.TOPIC, scheme -> reloadAnnotations());
  }

  @Override
  public void dispose() {
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

  private void updateStuff() {
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
  }

  private void checkByDirtyScope(@NotNull Set<String> removed,
                                 @NotNull Map<String, VcsRevisionNumber> refresh,
                                 @NotNull Set<? extends VirtualFile> files) {
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

  private void processUnderFile(@NotNull VirtualFile file) {
    final MultiMap<VirtualFile, FileAnnotation> annotations = new MultiMap<>();
    synchronized (myLock) {
      for (FileAnnotation fileAnnotation : myFileAnnotations) {
        VirtualFile virtualFile = fileAnnotation.getFile();
        if (virtualFile != null &&
            virtualFile.isInLocalFileSystem() &&
            VfsUtilCore.isAncestor(file, virtualFile, true)) {
          annotations.putValue(virtualFile, fileAnnotation);
        }
      }
    }
    if (!annotations.isEmpty()) {
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

  private void refreshForPath(@NotNull String path, @Nullable VcsRevisionNumber number) {
    final File file = new File(path);
    VirtualFile vf = myLocalFileSystem.findFileByIoFile(file);
    if (vf == null) {
      vf = myLocalFileSystem.refreshAndFindFileByIoFile(file);
    }
    if (vf == null) return;
    processFile(number, vf);
  }

  private void processFile(@Nullable VcsRevisionNumber number, @NotNull VirtualFile vf) {
    final Collection<FileAnnotation> annotations;
    synchronized (myLock) {
      annotations = ContainerUtil.filter(myFileAnnotations, it -> vf.equals(it.getFile()));
    }
    if (!annotations.isEmpty()) {
      if (number == null) {
        number = fromDiffProvider(vf);
      }
      if (number == null) return;

      VcsRevisionNumber finalNumber = number;
      List<FileAnnotation> copy = ContainerUtil.filter(annotations, it -> it.isBaseRevisionChanged(finalNumber));
      invalidateAnnotations(copy, false);
    }
  }

  private VcsRevisionNumber fromDiffProvider(@NotNull VirtualFile vf) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(vf);
    DiffProvider diffProvider;
    if (vcsRoot != null && vcsRoot.getVcs() != null && (diffProvider = vcsRoot.getVcs().getDiffProvider()) != null) {
      return diffProvider.getCurrentRevision(vf);
    }
    return null;
  }

  private void closeForVcs(@NotNull Set<VcsKey> refresh) {
    if (refresh.isEmpty()) return;
    synchronized (myLock) {
      List<FileAnnotation> copy = ContainerUtil.filter(myFileAnnotations, it -> refresh.contains(it.getVcsKey()));
      invalidateAnnotations(copy, false);
    }
  }

  @Override
  public void invalidateAnnotationsFor(@NotNull VirtualFile file, @Nullable VcsKey vcsKey) {
    synchronized (myLock) {
      Collection<FileAnnotation> copy = ContainerUtil.filter(myFileAnnotations, it ->
        file.equals(it.getFile()) && (vcsKey == null || vcsKey.equals(it.getVcsKey())));
      invalidateAnnotations(copy, false);
    }
  }

  private static void invalidateAnnotations(@NotNull Collection<? extends FileAnnotation> annotations, boolean reload) {
    if (annotations.isEmpty()) return;
    getApplication().invokeLater(() -> {
      for (FileAnnotation annotation : annotations) {
        try {
          if (reload) {
            try (AccessToken ignore = SlowOperations.knownIssue("IJPL-162976")) {
              annotation.reload(null);
            }
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

  @Override
  public void registerAnnotation(@NotNull FileAnnotation annotation) {
    synchronized (myLock) {
      myFileAnnotations.add(annotation);
    }
  }

  @Override
  public void unregisterAnnotation(@NotNull FileAnnotation annotation) {
    synchronized (myLock) {
      myFileAnnotations.remove(annotation);
    }
  }

  @Override
  public void reloadAnnotations() {
    synchronized (myLock) {
      List<FileAnnotation> copy = new ArrayList<>(myFileAnnotations);
      invalidateAnnotations(copy, true);
    }
  }

  @Override
  public void reloadAnnotationsForVcs(@NotNull VcsKey key) {
    synchronized (myLock) {
      List<FileAnnotation> copy = ContainerUtil.filter(myFileAnnotations, it -> key.equals(it.getVcsKey()));
      invalidateAnnotations(copy, true);
    }
  }

  private class MyRefresher implements VcsAnnotationRefresher {
    private final Runnable myUpdateStuff = () -> updateStuff();

    private void scheduleUpdate() {
      myUpdater.queue(myUpdateStuff);
    }

    @Override
    public void dirtyUnder(VirtualFile file) {
      if (file == null) return;
      synchronized (myLock) {
        myDirtyFiles.add(file);
      }
      scheduleUpdate();
    }

    @Override
    public void dirty(@NotNull BaseRevision currentRevision) {
      synchronized (myLock) {
        myDirtyChanges.put(currentRevision.getPath(), currentRevision.getRevision());
      }
      scheduleUpdate();
    }

    @Override
    public void dirty(@NotNull String path) {
      synchronized (myLock) {
        myDirtyPaths.add(path);
      }
      scheduleUpdate();
    }

    @Override
    public void configurationChanged(@NotNull VcsKey vcsKey) {
      synchronized (myLock) {
        myVcsKeySet.add(vcsKey);
      }
      scheduleUpdate();
    }
  }
}
