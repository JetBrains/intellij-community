// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector.ANNOTATE_ACTIVITY;

@ApiStatus.Internal
public final class AnnotateLocalFileAction {
  private static final Logger LOG = Logger.getInstance(AnnotateLocalFileAction.class);

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) return false;

    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null || file.isDirectory() || file.getFileType().isBinary()) return false;

    VirtualFile vcsFile = VcsUtil.resolveSymlinkIfNeeded(project, file);
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vcsFile);
    if (vcs == null) return false;

    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return false;

    FileStatus fileStatus = ChangeListManager.getInstance(project).getStatus(vcsFile);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return false;
    }

    return true;
  }

  private static boolean isSuspended(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
    return VcsAnnotateUtil.getBackgroundableLock(project, file).isLocked();
  }

  private static boolean isAnnotated(@NotNull AnActionEvent e) {
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
    Editor editor = VcsAnnotateUtil.getEditorFor(file, e.getDataContext());
    return editor != null && AnnotateToggleAction.hasVcsAnnotations(editor);
  }

  private static void perform(AnActionEvent e, boolean selected) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile selectedFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);

    if (!selected) {
      Editor editor = Objects.requireNonNull(VcsAnnotateUtil.getEditorFor(selectedFile, e.getDataContext()));
      AnnotateToggleAction.closeVcsAnnotations(editor);
    }
    else {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null && !VcsAnnotateUtil.isEditorForFile(editor, selectedFile)) {
        editor = null;
      }

      if (editor == null) {
        editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, selectedFile), false);
        if (editor == null) {
          Messages.showErrorDialog(project,
                                   VcsBundle.message("dialog.message.can.t.create.text.editor.for", selectedFile.getPresentableUrl()),
                                   VcsBundle.message("message.title.annotate"));
          return;
        }
      }

      doAnnotate(editor, e, project);
    }
  }

  private static void doAnnotate(final @NotNull Editor editor, AnActionEvent e, final @NotNull Project project) {
    StructuredIdeActivity activity = ANNOTATE_ACTIVITY.started(project);
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (file == null) return;

    VirtualFile vcsFile = VcsUtil.resolveSymlinkIfNeeded(project, file);
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vcsFile);
    if (vcs == null) return;

    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    assert annotationProvider != null;

    final Ref<FileAnnotation> fileAnnotationRef = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();

    final BackgroundableActionLock actionLock = VcsAnnotateUtil.getBackgroundableLock(project, file);
    actionLock.lock();

    final Task.Backgroundable annotateTask = new Task.Backgroundable(project, VcsBundle.message("retrieving.annotations"), true) {
      @Override
      public void run(final @NotNull ProgressIndicator indicator) {
        try {
          fileAnnotationRef.set(annotationProvider.annotate(vcsFile));
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
        catch (ProcessCanceledException pce) {
          throw pce;
        }
        catch (Throwable t) {
          exceptionRef.set(new VcsException(t));
        }
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        if (!exceptionRef.isNull()) {
          LOG.warn(exceptionRef.get());
          AbstractVcsHelper.getInstance(project)
            .showErrors(Collections.singletonList(exceptionRef.get()), VcsBundle.message("message.title.annotate"));
        }

        if (!fileAnnotationRef.isNull()) {
          AnnotateToggleAction.doAnnotate(editor, project, fileAnnotationRef.get(), vcs);
        }
        List<EventPair<?>> eventData = new ArrayList<>();
        String place = e.getPlace();
        eventData.add(EventFields.ActionPlace.with(place));
        eventData.add(ActionsEventLogGroup.CONTEXT_MENU.with(e.isFromContextMenu()));
        activity.finished(() -> eventData);
      }

      @Override
      public void onFinished() {
        actionLock.unlock();
      }
    };
    ProgressManager.getInstance().run(annotateTask);
  }

  @ApiStatus.Internal
  public static final class Provider implements AnnotateToggleAction.Provider {
    @Override
    public boolean isEnabled(AnActionEvent e) {
      return AnnotateLocalFileAction.isEnabled(e);
    }

    @Override
    public boolean isSuspended(@NotNull AnActionEvent e) {
      return AnnotateLocalFileAction.isSuspended(e);
    }

    @Override
    public boolean isAnnotated(AnActionEvent e) {
      return AnnotateLocalFileAction.isAnnotated(e);
    }

    @Override
    public void perform(@NotNull AnActionEvent e, boolean selected) {
      AnnotateLocalFileAction.perform(e, selected);
    }
  }
}
