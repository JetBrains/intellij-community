// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AnnotateLocalFileAction {
  private static final Logger LOG = Logger.getInstance(AnnotateLocalFileAction.class);

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) return false;

    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null || file.isDirectory() || file.getFileType().isBinary()) return false;

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return false;

    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return false;

    FileStatus fileStatus = ChangeListManager.getInstance(project).getStatus(file);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return false;
    }

    return true;
  }

  private static boolean isSuspended(@NotNull AnActionEvent e) {
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
    return VcsAnnotateUtil.getBackgroundableLock(e.getRequiredData(CommonDataKeys.PROJECT), file).isLocked();
  }

  private static boolean isAnnotated(@NotNull AnActionEvent e) {
    List<Editor> editors = getEditors(e.getDataContext());
    return ContainerUtil.exists(editors, editor -> AnnotateToggleAction.hasVcsAnnotations(editor));
  }

  private static void perform(AnActionEvent e, boolean selected) {
    if (!selected) {
      List<Editor> editors = getEditors(e.getDataContext());
      for (Editor editor : editors) {
        AnnotateToggleAction.closeVcsAnnotations(editor);
      }
    }
    else {
      Project project = Objects.requireNonNull(e.getProject());

      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor == null) {
        VirtualFile selectedFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
        FileEditor[] fileEditors = FileEditorManager.getInstance(project).openFile(selectedFile, false);
        for (FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }

        if (editor == null) {
          Messages.showErrorDialog(project,
                                   VcsBundle.message("dialog.message.can.t.create.text.editor.for", selectedFile.getPresentableUrl()),
                                   VcsBundle.message("message.title.annotate"));
          LOG.warn(String.format("Can't create text editor for file: valid - %s; file type - %s; editors - %s", //NON-NLS
                                 selectedFile.isValid(), selectedFile.getFileType().getName(), Arrays.toString(fileEditors)));

          return;
        }
      }

      doAnnotate(editor, project);
    }
  }

  private static void doAnnotate(@NotNull final Editor editor, @NotNull final Project project) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (file == null) return;

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
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
          fileAnnotationRef.set(annotationProvider.annotate(file));
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
          AbstractVcsHelper.getInstance(project).showErrors(Collections.singletonList(exceptionRef.get()), VcsBundle.message("message.title.annotate"));
        }

        if (!fileAnnotationRef.isNull()) {
          AnnotateToggleAction.doAnnotate(editor, project, fileAnnotationRef.get(), vcs);
        }
      }

      @Override
      public void onFinished() {
        actionLock.unlock();
      }
    };
    ProgressManager.getInstance().run(annotateTask);
  }

  @NotNull
  private static List<Editor> getEditors(@NotNull DataContext context) {
    Editor editor = context.getData(CommonDataKeys.EDITOR);
    if (editor != null) return Collections.singletonList(editor);

    Project project = context.getData(CommonDataKeys.PROJECT);
    VirtualFile file = context.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || file == null) return Collections.emptyList();

    return VcsAnnotateUtil.getEditors(project, file);
  }

  public static class Provider implements AnnotateToggleAction.Provider {
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
