package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.ObjectUtils.notNull;

public abstract class AnnotateRevisionActionBase extends DumbAwareAction {
  public AnnotateRevisionActionBase(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Nullable
  protected abstract AbstractVcs getVcs(@NotNull AnActionEvent e);

  @Nullable
  protected abstract VirtualFile getFile(@NotNull AnActionEvent e);

  @Nullable
  protected abstract VcsFileRevision getFileRevision(@NotNull AnActionEvent e);

  @Nullable
  protected Editor getEditor(@NotNull AnActionEvent e) {
    return null;
  }

  protected int getAnnotatedLine(@NotNull AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor == null ? 0 : editor.getCaretModel().getLogicalPosition().line;
  }

  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  public boolean isEnabled(@NotNull AnActionEvent e) {
    if (e.getProject() == null) return false;

    return isEnabled(getVcs(e), getFile(e), getFileRevision(e));
  }

  public static boolean isEnabled(@Nullable AbstractVcs vcs,
                                  @Nullable VirtualFile file,
                                  @Nullable VcsFileRevision fileRevision) {
    if (VcsHistoryUtil.isEmpty(fileRevision) || file == null || vcs == null) return false;

    AnnotationProvider provider = vcs.getAnnotationProvider();
    if (provider == null || !provider.isAnnotationValid(fileRevision)) return false;
    if (VcsAnnotateUtil.getBackgroundableLock(vcs.getProject(), file).isLocked()) return false;

    return true;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final VcsFileRevision fileRevision = getFileRevision(e);
    final VirtualFile file = getFile(e);
    final AbstractVcs vcs = getVcs(e);

    annotate(notNull(file), notNull(fileRevision), notNull(vcs), getEditor(e), getAnnotatedLine(e));
  }

  public static void annotate(@NotNull VirtualFile file,
                              @NotNull VcsFileRevision fileRevision,
                              @NotNull AbstractVcs vcs,
                              @Nullable Editor editor,
                              int annotatedLine) {
    final CharSequence oldContent = editor == null ? null : editor.getDocument().getImmutableCharSequence();

    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    assert annotationProvider != null;

    final Ref<FileAnnotation> fileAnnotationRef = new Ref<>();
    final Ref<Integer> newLineRef = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();

    VcsAnnotateUtil.getBackgroundableLock(vcs.getProject(), file).lock();

    Semaphore semaphore = new Semaphore(0);
    AtomicBoolean shouldOpenEditorInSync = new AtomicBoolean(true);

    ProgressManager.getInstance().run(new Task.Backgroundable(vcs.getProject(), VcsBundle.message("retrieving.annotations"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          FileAnnotation fileAnnotation = annotationProvider.annotate(file, fileRevision);

          int newLine = translateLine(oldContent, fileAnnotation.getAnnotatedContent(), annotatedLine);

          fileAnnotationRef.set(fileAnnotation);
          newLineRef.set(newLine);

          shouldOpenEditorInSync.set(false);
          semaphore.release();
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
      }

      @Override
      public void onFinished() {
        VcsAnnotateUtil.getBackgroundableLock(vcs.getProject(), file).unlock();
      }

      @Override
      public void onSuccess() {
        if (!exceptionRef.isNull()) {
          AbstractVcsHelper.getInstance(myProject).showError(exceptionRef.get(), VcsBundle.message("operation.name.annotate"));
        }
        if (fileAnnotationRef.isNull()) return;

        AbstractVcsHelper.getInstance(myProject).showAnnotation(fileAnnotationRef.get(), file, vcs, newLineRef.get());
      }
    });

    try {
      semaphore.tryAcquire(ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS, TimeUnit.MILLISECONDS);

      // We want to let Backgroundable task open editor if it was fast enough.
      // This will remove blinking on editor opening (step 1 - editor opens, step 2 - annotations are shown).
      if (shouldOpenEditorInSync.get()) {
        CharSequence content = LoadTextUtil.loadText(file);
        int newLine = translateLine(oldContent, content, annotatedLine);

        OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(vcs.getProject(), file, newLine, 0);
        FileEditorManager.getInstance(vcs.getProject()).openTextEditor(openFileDescriptor, true);
      }
    }
    catch (InterruptedException ignore) {
    }
  }

  private static int translateLine(@Nullable CharSequence oldContent, @Nullable CharSequence newContent, int line) {
    if (oldContent == null || newContent == null) return line;
    try {
      return Diff.translateLine(oldContent, newContent, line, true);
    }
    catch (FilesTooBigForDiffException ignore) {
      return line;
    }
  }
}
