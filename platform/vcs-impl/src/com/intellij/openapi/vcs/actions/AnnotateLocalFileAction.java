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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class AnnotateLocalFileAction {
  private static final Logger LOG = Logger.getInstance(AnnotateLocalFileAction.class);

  private static boolean isEnabled(AnActionEvent e) {
    VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);

    Project project = context.getProject();
    if (project == null || project.isDisposed()) return false;

    VirtualFile file = context.getSelectedFile();
    if (file == null || file.isDirectory() || file.getFileType().isBinary()) return false;

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return false;

    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return false;

    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return false;
    }

    return true;
  }

  private static boolean isSuspended(AnActionEvent e) {
    VirtualFile file = assertNotNull(VcsContextFactory.SERVICE.getInstance().createContextOn(e).getSelectedFile());
    return VcsAnnotateUtil.getBackgroundableLock(e.getRequiredData(CommonDataKeys.PROJECT), file).isLocked();
  }

  private static boolean isAnnotated(AnActionEvent e) {
    VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);

    Editor editor = context.getEditor();
    if (editor != null) {
      return editor.getGutter().isAnnotationsShown();
    }

    return ContainerUtil.exists(getEditors(context), new Condition<Editor>() {
      @Override
      public boolean value(Editor editor) {
        return editor.getGutter().isAnnotationsShown();
      }
    });
  }

  private static void perform(AnActionEvent e, boolean selected) {
    final VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);

    if (!selected) {
      for (Editor editor : getEditors(context)) {
        editor.getGutter().closeAllAnnotations();
      }
    }
    else {
      Project project = assertNotNull(context.getProject());
      VirtualFile selectedFile = assertNotNull(context.getSelectedFile());

      Editor editor = context.getEditor();
      if (editor == null) {
        FileEditor[] fileEditors = FileEditorManager.getInstance(project).openFile(selectedFile, false);
        for (FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }
      }
      LOG.assertTrue(editor != null);
      doAnnotate(editor, project);
    }
  }

  private static void doAnnotate(@NotNull final Editor editor, @NotNull final Project project) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (file == null) return;

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return;

    final AnnotationProvider annotationProvider = vcs.getCachingAnnotationProvider();
    assert annotationProvider != null;

    final Ref<FileAnnotation> fileAnnotationRef = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();

    VcsAnnotateUtil.getBackgroundableLock(project, file).lock();

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
        VcsAnnotateUtil.getBackgroundableLock(project, file).unlock();

        if (!exceptionRef.isNull()) {
          LOG.warn(exceptionRef.get());
          AbstractVcsHelper.getInstance(project).showErrors(Collections.singletonList(exceptionRef.get()), VcsBundle.message("message.title.annotate"));
        }

        if (!fileAnnotationRef.isNull()) {
          AnnotateToggleAction.doAnnotate(editor, project, file, fileAnnotationRef.get(), vcs);
        }
      }
    };
    ProgressManager.getInstance().run(annotateTask);
  }

  @NotNull
  private static List<Editor> getEditors(@NotNull VcsContext context) {
    Project project = assertNotNull(context.getProject());
    VirtualFile file = assertNotNull(context.getSelectedFile());
    return VcsAnnotateUtil.getEditors(project, file);
  }

  public static class Provider implements AnnotateToggleAction.Provider {
    @Override
    public boolean isEnabled(AnActionEvent e) {
      return AnnotateLocalFileAction.isEnabled(e);
    }

    @Override
    public boolean isSuspended(AnActionEvent e) {
      return AnnotateLocalFileAction.isSuspended(e);
    }

    @Override
    public boolean isAnnotated(AnActionEvent e) {
      return AnnotateLocalFileAction.isAnnotated(e);
    }

    @Override
    public void perform(AnActionEvent e, boolean selected) {
      AnnotateLocalFileAction.perform(e, selected);
    }
  }
}
