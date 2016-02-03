package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class AnnotateRevisionActionBase extends AnAction {
  public AnnotateRevisionActionBase(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Nullable
  protected abstract AbstractVcs getVcs(@NotNull AnActionEvent e);

  @Nullable
  protected abstract VirtualFile getFile(@NotNull AnActionEvent e);

  @Nullable
  protected abstract VcsFileRevision getFileRevision(@NotNull AnActionEvent e);

  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  public boolean isEnabled(@NotNull AnActionEvent e) {
    if (e.getProject() == null) return false;

    VcsFileRevision fileRevision = getFileRevision(e);
    if (fileRevision == null) return false;

    VirtualFile file = getFile(e);
    if (file == null) return false;

    AbstractVcs vcs = getVcs(e);
    if (vcs == null) return false;

    AnnotationProvider provider = vcs.getCachingAnnotationProvider();
    if (provider == null || !provider.isAnnotationValid(fileRevision)) return false;

    if (getBackgroundableLock(vcs.getProject(), file).isLocked()) return false;

    return true;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final VcsFileRevision fileRevision = getFileRevision(e);
    final VirtualFile file = getFile(e);
    final AbstractVcs vcs = getVcs(e);
    assert vcs != null;
    assert file != null;
    assert fileRevision != null;

    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    final CharSequence oldContent = editor == null ? null : editor.getDocument().getImmutableCharSequence();
    final int oldLine = editor == null ? 0 : editor.getCaretModel().getLogicalPosition().line;

    final AnnotationProvider annotationProvider = vcs.getCachingAnnotationProvider();
    assert annotationProvider != null;

    final Ref<FileAnnotation> fileAnnotationRef = new Ref<FileAnnotation>();
    final Ref<Integer> newLineRef = new Ref<Integer>();
    final Ref<VcsException> exceptionRef = new Ref<VcsException>();

    getBackgroundableLock(vcs.getProject(), file).lock();

    ProgressManager.getInstance().run(new Task.Backgroundable(vcs.getProject(), VcsBundle.message("retrieving.annotations"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          FileAnnotation fileAnnotation = annotationProvider.annotate(file, fileRevision);

          int newLine = oldLine;
          if (oldContent != null) {
            String content = fileAnnotation.getAnnotatedContent();
            try {
              newLine = Diff.translateLine(oldContent, content, oldLine, true);
            }
            catch (FilesTooBigForDiffException ignore) {
            }
          }

          fileAnnotationRef.set(fileAnnotation);
          newLineRef.set(newLine);
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        getBackgroundableLock(vcs.getProject(), file).unlock();

        if (!exceptionRef.isNull()) {
          AbstractVcsHelper.getInstance(myProject).showError(exceptionRef.get(), VcsBundle.message("operation.name.annotate"));
        }
        if (fileAnnotationRef.isNull()) return;

        AbstractVcsHelper.getInstance(myProject).showAnnotation(fileAnnotationRef.get(), file, vcs, newLineRef.get());
      }
    });
  }

  @NotNull
  private static BackgroundableActionLock getBackgroundableLock(@NotNull Project project, @NotNull VirtualFile file) {
    return AnnotateToggleAction.getBackgroundableLock(project, file);
  }
}
