// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcs.CacheableAnnotationProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class AnnotationsPreloader {
  private static final Logger LOG = Logger.getInstance(AnnotationsPreloader.class);

  private final MergingUpdateQueue myUpdateQueue;
  private final Project myProject;

  public AnnotationsPreloader(final Project project) {
    myProject = project;
    myUpdateQueue = new MergingUpdateQueue("Annotations preloader queue", 1000, true, null, project, null, false);

    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (!isEnabled()) return;
        VirtualFile file = event.getNewFile();
        if (file != null) {
          schedulePreloading(file);
        }
      }
    });
  }

  private static boolean isEnabled() {
    // TODO: check cores number?
    return Registry.is("vcs.annotations.preload") && !PowerSaveMode.isEnabled();
  }

  private void schedulePreloading(@NotNull final VirtualFile file) {
    if (myProject.isDisposed() || file.getFileType().isBinary()) return;

    myUpdateQueue.queue(new Update(file) {
      @Override
      public void run() {
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
          try {
            long start = 0;
            if (LOG.isDebugEnabled()) {
              start = System.currentTimeMillis();
            }
            if (!FileEditorManager.getInstance(myProject).isFileOpen(file)) return;

            FileStatus fileStatus = ChangeListManager.getInstance(myProject).getStatus(file);
            if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
              return;
            }

            AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
            if (vcs == null) return;

            CacheableAnnotationProvider annotationProvider = ObjectUtils.tryCast(vcs.getAnnotationProvider(),
                                                                                 CacheableAnnotationProvider.class);
            if (annotationProvider == null) return;

            annotationProvider.populateCache(file);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Preloaded VCS annotations for ", file.getName(), " in ", String.valueOf(System.currentTimeMillis() - start), "ms");
            }
          }
          catch (VcsException e) {
            LOG.info(e);
          }
        });
      }
    });
  }
}
