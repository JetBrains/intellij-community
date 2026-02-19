// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.AnnotationProviderEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;

@ApiStatus.Internal
public final class AnnotateVcsVirtualFileAction {
  private static final Logger LOG = Logger.getInstance(AnnotateVcsVirtualFileAction.class);

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDisposed()) return false;

    VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (selectedFiles == null || selectedFiles.length != 1) return false;

    VirtualFile file = selectedFiles[0];
    if (file.isDirectory() || file.getFileType().isBinary()) return false;

    if (VcsAnnotateUtil.getEditorFor(file, e.getDataContext()) == null) return false;

    AnnotationData data = AnnotationData.extractFrom(project, file);
    if (data == null) return false;

    AnnotationProviderEx provider = ObjectUtils.tryCast(data.getVcs().getAnnotationProvider(), AnnotationProviderEx.class);
    if (provider == null) return false;

    return provider.isAnnotationValid(data.getFilePath(), data.getRevisionNumber());
  }

  private static boolean isSuspended(@NotNull AnActionEvent e) {
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY)[0];
    return VcsAnnotateUtil.getBackgroundableLock(e.getRequiredData(CommonDataKeys.PROJECT), file).isLocked();
  }

  private static boolean isAnnotated(@NotNull AnActionEvent e) {
    final VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY)[0];
    Editor editor = VcsAnnotateUtil.getEditorFor(file, e.getDataContext());
    return editor != null && AnnotateToggleAction.hasVcsAnnotations(editor);
  }

  private static void perform(AnActionEvent e, boolean selected) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY)[0];
    Editor editor = Objects.requireNonNull(VcsAnnotateUtil.getEditorFor(file, e.getDataContext()));

    if (!selected) {
      AnnotateToggleAction.closeVcsAnnotations(editor);
    }
    else {
      doAnnotate(project, editor, file);
    }
  }

  private static void doAnnotate(final @NotNull Project project, final @NotNull Editor editor, final @NotNull VirtualFile file) {
    final AnnotationData data = AnnotationData.extractFrom(project, file);
    assert data != null;

    final AnnotationProviderEx provider = (AnnotationProviderEx)data.getVcs().getAnnotationProvider();
    assert provider != null;


    final Ref<FileAnnotation> fileAnnotationRef = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();

    final BackgroundableActionLock actionLock = VcsAnnotateUtil.getBackgroundableLock(project, file);
    actionLock.lock();

    final Task.Backgroundable annotateTask = new Task.Backgroundable(project, VcsBundle.message("retrieving.annotations"), true) {
      @Override
      public void run(final @NotNull ProgressIndicator indicator) {
        try {
          fileAnnotationRef.set(provider.annotate(data.getFilePath(), data.getRevisionNumber()));
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
          AnnotateToggleAction.doAnnotate(editor, project, fileAnnotationRef.get(), data.getVcs());
        }
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
      return AnnotateVcsVirtualFileAction.isEnabled(e);
    }

    @Override
    public boolean isSuspended(@NotNull AnActionEvent e) {
      return AnnotateVcsVirtualFileAction.isSuspended(e);
    }

    @Override
    public boolean isAnnotated(AnActionEvent e) {
      return AnnotateVcsVirtualFileAction.isAnnotated(e);
    }

    @Override
    public void perform(@NotNull AnActionEvent e, boolean selected) {
      AnnotateVcsVirtualFileAction.perform(e, selected);
    }
  }
}
