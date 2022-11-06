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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;

public class AnnotateVcsVirtualFileAction {
  private static final Logger LOG = Logger.getInstance(AnnotateVcsVirtualFileAction.class);

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDisposed()) return false;

    VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (selectedFiles == null || selectedFiles.length != 1) return false;

    VirtualFile file = selectedFiles[0];
    if (file.isDirectory() || file.getFileType().isBinary()) return false;

    if (VcsAnnotateUtil.getEditorFor(file, e.getDataContext()) == null) return false;

    AnnotationData data = extractData(project, file);
    if (data == null) return false;

    AnnotationProviderEx provider = ObjectUtils.tryCast(data.vcs.getAnnotationProvider(), AnnotationProviderEx.class);
    if (provider == null) return false;

    return provider.isAnnotationValid(data.filePath, data.revisionNumber);
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

  private static void doAnnotate(@NotNull final Project project, @NotNull final Editor editor, @NotNull final VirtualFile file) {
    final AnnotationData data = extractData(project, file);
    assert data != null;

    final AnnotationProviderEx provider = (AnnotationProviderEx)data.vcs.getAnnotationProvider();
    assert provider != null;


    final Ref<FileAnnotation> fileAnnotationRef = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();

    final BackgroundableActionLock actionLock = VcsAnnotateUtil.getBackgroundableLock(project, file);
    actionLock.lock();

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
        if (!exceptionRef.isNull()) {
          LOG.warn(exceptionRef.get());
          AbstractVcsHelper.getInstance(project)
            .showErrors(Collections.singletonList(exceptionRef.get()), VcsBundle.message("message.title.annotate"));
        }

        if (!fileAnnotationRef.isNull()) {
          AnnotateToggleAction.doAnnotate(editor, project, fileAnnotationRef.get(), data.vcs);
        }
      }

      @Override
      public void onFinished() {
        actionLock.unlock();
      }
    };
    ProgressManager.getInstance().run(annotateTask);
  }

  @Nullable
  private static AnnotationData extractData(@NotNull Project project, @NotNull VirtualFile file) {
    FilePath filePath = null;
    VcsRevisionNumber revisionNumber = null;
    if (file instanceof VcsVirtualFile) {
      VcsFileRevision revision = ((VcsVirtualFile)file).getFileRevision();
      if (revision instanceof VcsFileRevisionEx) {
        filePath = ((VcsFileRevisionEx)revision).getPath();
      }
      else {
        filePath = VcsUtil.getFilePath(file.getPath());
      }
      revisionNumber = revision != null ? revision.getRevisionNumber() : null;
    }
    else if (file instanceof ContentRevisionVirtualFile) {
      ContentRevision revision = ((ContentRevisionVirtualFile)file).getContentRevision();
      filePath = revision.getFile();
      revisionNumber = revision.getRevisionNumber();
    }
    if (filePath == null || revisionNumber == null) return null;
    if (revisionNumber instanceof TextRevisionNumber ||
        revisionNumber == VcsRevisionNumber.NULL) {
      return null;
    }

    AbstractVcs vcs = VcsUtil.getVcsFor(project, filePath);
    return vcs != null ? new AnnotationData(vcs, filePath, revisionNumber) : null;
  }

  private static class AnnotationData {
    @NotNull public final AbstractVcs vcs;
    @NotNull public final FilePath filePath;
    @NotNull public final VcsRevisionNumber revisionNumber;

    AnnotationData(@NotNull AbstractVcs vcs,
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
