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
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AnnotateVcsVirtualFileAction {
  private static final Logger LOG = Logger.getInstance(AnnotateVcsVirtualFileAction.class);

  private static boolean isEnabled(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDisposed()) return false;

    VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (selectedFiles == null || selectedFiles.length != 1) return false;

    VirtualFile file = selectedFiles[0];
    if (file.isDirectory() || file.getFileType().isBinary()) return false;

    if (VcsAnnotateUtil.getEditors(project, file).isEmpty()) return false;

    AnnotationData data = extractData(project, file);
    if (data == null) return false;

    AnnotationProvider provider = data.vcs.getAnnotationProvider();
    return provider instanceof AnnotationProviderEx;
  }

  private static boolean isSuspended(AnActionEvent e) {
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY)[0];
    return VcsAnnotateUtil.getBackgroundableLock(e.getRequiredData(CommonDataKeys.PROJECT), file).isLocked();
  }

  private static boolean isAnnotated(AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY)[0];
    List<Editor> editors = VcsAnnotateUtil.getEditors(project, file);
    return ContainerUtil.exists(editors, new Condition<Editor>() {
      @Override
      public boolean value(Editor editor) {
        return editor.getGutter().isAnnotationsShown();
      }
    });
  }

  private static void perform(AnActionEvent e, boolean selected) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY)[0];
    List<Editor> editors = VcsAnnotateUtil.getEditors(project, file);

    if (!selected) {
      for (Editor editor : editors) {
        editor.getGutter().closeAllAnnotations();
      }
    }
    else {
      final Editor editor = editors.get(0);
      doAnnotate(project, editor, file);
    }
  }

  private static void doAnnotate(@NotNull final Project project, @NotNull final Editor editor, @NotNull final VirtualFile file) {
    final AnnotationData data = extractData(project, file);
    assert data != null;

    final AnnotationProviderEx provider = (AnnotationProviderEx)data.vcs.getAnnotationProvider();
    assert provider != null;


    final Ref<FileAnnotation> fileAnnotationRef = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();

    VcsAnnotateUtil.getBackgroundableLock(project, file).lock();

    final Task.Backgroundable annotateTask = new Task.Backgroundable(project, VcsBundle.message("retrieving.annotations"), true) {
      @Override
      public void run(final @NotNull ProgressIndicator indicator) {
        try {
          fileAnnotationRef.set(provider.annotate(data.filePath, data.revisionNumber));
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
          AnnotateToggleAction.doAnnotate(editor, project, null, fileAnnotationRef.get(), data.vcs);
        }
      }
    };
    ProgressManager.getInstance().run(annotateTask);
  }

  @Nullable
  private static AnnotationData extractData(@NotNull Project project, @NotNull VirtualFile file) {
    FilePath filePath = null;
    VcsRevisionNumber revisionNumber = null;
    if (file instanceof VcsVirtualFile) {
      filePath = VcsUtil.getFilePath(file.getPath());
      VcsFileRevision revision = ((VcsVirtualFile)file).getFileRevision();
      revisionNumber = revision != null ? revision.getRevisionNumber() : null;
    }
    else if (file instanceof ContentRevisionVirtualFile) {
      ContentRevision revision = ((ContentRevisionVirtualFile)file).getContentRevision();
      filePath = revision.getFile();
      revisionNumber = revision.getRevisionNumber();
    }
    if (filePath == null || revisionNumber == null) return null;

    AbstractVcs vcs = VcsUtil.getVcsFor(project, filePath);
    return vcs != null ? new AnnotationData(vcs, filePath, revisionNumber) : null;
  }

  private static class AnnotationData {
    @NotNull public final AbstractVcs vcs;
    @NotNull public final FilePath filePath;
    @NotNull public final VcsRevisionNumber revisionNumber;

    public AnnotationData(@NotNull AbstractVcs vcs,
                          @NotNull FilePath filePath,
                          @NotNull VcsRevisionNumber revisionNumber) {
      this.vcs = vcs;
      this.filePath = filePath;
      this.revisionNumber = revisionNumber;
    }
  }

  public static class Provider implements AnnotateToggleAction.Provider {
    @Override
    public boolean isEnabled(AnActionEvent e) {
      return AnnotateVcsVirtualFileAction.isEnabled(e);
    }

    @Override
    public boolean isSuspended(AnActionEvent e) {
      return AnnotateVcsVirtualFileAction.isSuspended(e);
    }

    @Override
    public boolean isAnnotated(AnActionEvent e) {
      return AnnotateVcsVirtualFileAction.isAnnotated(e);
    }

    @Override
    public void perform(AnActionEvent e, boolean selected) {
      AnnotateVcsVirtualFileAction.perform(e, selected);
    }
  }
}
