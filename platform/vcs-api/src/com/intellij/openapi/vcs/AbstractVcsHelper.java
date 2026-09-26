// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.ide.errorTreeView.HotfixData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitResultHandler;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Component which provides means to invoke different VCS-related services.
 */
public abstract class AbstractVcsHelper {

  protected final @NotNull Project myProject;

  protected AbstractVcsHelper(@NotNull Project project) {
    myProject = project;
  }

  public static @NotNull AbstractVcsHelper getInstance(Project project) {
    return project.getService(AbstractVcsHelper.class);
  }

  public abstract void showErrors(List<? extends VcsException> abstractVcsExceptions, @NotNull @TabTitle String tabDisplayName);

  public abstract void showErrors(Map<HotfixData, List<VcsException>> exceptionGroups, @NotNull @TabTitle String tabDisplayName);

  /**
   * Runs the runnable inside the vcs transaction (if needed), collects all exceptions, commits/rollbacks transaction
   * and returns all exceptions together.
   */
  public abstract List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters);

  public void showError(final VcsException e, @TabTitle String tabDisplayName) {
    showErrors(Collections.singletonList(e), tabDisplayName);
  }

  public abstract void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs);

  public abstract void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs, int line);

  public abstract void showChangesListBrowser(@NotNull CommittedChangeList changelist, @Nullable @NlsContexts.DialogTitle String title);

  public abstract void showWhatDiffersBrowser(@NotNull Collection<Change> changes, @Nullable @NlsContexts.DialogTitle String title);

  public abstract void showCommittedChangesBrowser(@NotNull CommittedChangesProvider provider,
                                                   @NotNull RepositoryLocation location,
                                                   @Nullable @NlsContexts.DialogTitle String title,
                                                   @Nullable Component parent);

  public abstract void openCommittedChangesTab(@NotNull CommittedChangesProvider provider,
                                               @NotNull RepositoryLocation location,
                                               @NotNull ChangeBrowserSettings settings,
                                               int maxCount,
                                               @Nullable @NlsContexts.DialogTitle String title);

  /**
   * Shows the multiple file merge dialog for resolving conflicts in the specified set of virtual files.
   * Assumes all files are under the same VCS.
   * <p>
   * Returns a result containing both the processed files and
   * whether the user explicitly accepted and finished the merge (vs just closing the dialog).
   */
  public abstract @NotNull MergeDialogResult showMergeDialogWithResult(@NotNull List<? extends VirtualFile> files,
                                                                       @NotNull MergeProvider provider,
                                                                       @NotNull MergeDialogCustomizer mergeDialogCustomizer);

  /**
   * {@link #showMergeDialogWithResult(List, MergeProvider, MergeDialogCustomizer)} with a default customizer.
   */
  public final @NotNull MergeDialogResult showMergeDialogWithResult(@NotNull List<? extends VirtualFile> files,
                                                                    @NotNull MergeProvider provider) {
    return showMergeDialogWithResult(files, provider, provider.createDefaultMergeDialogCustomizer());
  }

  /**
   * {@link #showMergeDialogWithResult(List, MergeProvider)} with a default merge provider for the current VCS.
   */
  public final @NotNull MergeDialogResult showMergeDialogWithResult(@NotNull List<? extends VirtualFile> files) {
    if (files.isEmpty()) return EmptyMergeDialogResult.INSTANCE;
    MergeProvider provider = null;
    for (VirtualFile virtualFile : files) {
      final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
      if (vcs != null) {
        provider = vcs.getMergeProvider();
        if (provider != null) break;
      }
    }
    if (provider == null) return EmptyMergeDialogResult.INSTANCE;
    return showMergeDialogWithResult(files, provider);
  }

  /**
   * @deprecated Use {@link #showMergeDialogWithResult(List, MergeProvider)} instead.
   */
  @Deprecated
  public final @NotNull List<VirtualFile> showMergeDialog(List<? extends VirtualFile> files, MergeProvider provider) {
    return showMergeDialogWithResult(files, provider).getProcessedFiles();
  }

  /**
   * @deprecated Use {@link #showMergeDialogWithResult(List)} instead.
   */
  @Deprecated
  public final @NotNull List<VirtualFile> showMergeDialog(List<? extends VirtualFile> files) {
    return showMergeDialogWithResult(files).getProcessedFiles();
  }

  public interface MergeDialogResult {
    @NotNull List<VirtualFile> getProcessedFiles();

    boolean shouldFinishMerge();
  }

  private enum EmptyMergeDialogResult implements MergeDialogResult {
    INSTANCE;

    @Override
    public @NotNull List<VirtualFile> getProcessedFiles() { return Collections.emptyList(); }

    @Override
    public boolean shouldFinishMerge() { return true; }
  }

  public abstract void showFileHistory(@NotNull VcsHistoryProvider historyProvider, @NotNull FilePath path, @NotNull AbstractVcs vcs);

  public abstract void showFileHistory(@NotNull VcsHistoryProvider historyProvider,
                                       @Nullable AnnotationProvider annotationProvider,
                                       @NotNull FilePath path,
                                       @NotNull AbstractVcs vcs);

  public abstract @Nullable Collection<VirtualFile> selectFilesToProcess(List<? extends VirtualFile> files,
                                                                         @NlsContexts.DialogTitle String title,
                                                                         @NlsContexts.DialogMessage @Nullable String prompt,
                                                                         @NlsContexts.DialogTitle @Nullable String singleFileTitle,
                                                                         @NlsContexts.DialogMessage @Nullable String singleFilePromptTemplate,
                                                                         @NotNull VcsShowConfirmationOption confirmationOption);

  public abstract @Nullable Collection<FilePath> selectFilePathsToProcess(@NotNull List<? extends FilePath> files,
                                                                          @NlsContexts.DialogTitle String title,
                                                                          @NlsContexts.DialogMessage @Nullable String prompt,
                                                                          @NlsContexts.DialogTitle @Nullable String singleFileTitle,
                                                                          @NlsContexts.DialogMessage @Nullable String singleFilePromptTemplate,
                                                                          @NotNull VcsShowConfirmationOption confirmationOption);

  public abstract @Nullable Collection<FilePath> selectFilePathsToProcess(@NotNull List<? extends FilePath> files,
                                                                          @NlsContexts.DialogTitle String title,
                                                                          @NlsContexts.DialogMessage @Nullable String prompt,
                                                                          @NlsContexts.DialogTitle @Nullable String singleFileTitle,
                                                                          @NlsContexts.DialogMessage @Nullable String singleFilePromptTemplate,
                                                                          @NotNull VcsShowConfirmationOption confirmationOption,
                                                                          @NlsActions.ActionText @Nullable String okActionName,
                                                                          @NlsActions.ActionText @Nullable String cancelActionName);


  /**
   * <p>Shows commit dialog, fills it with the given changes and given commit message, initially selects the given changelist.</p>
   * <p>Note that the method is asynchronous: it returns right after user presses "Commit" or "Cancel" and after all pre-commit handlers
   * have been called. It doesn't wait for commit itself to succeed or fail - for this use the {@code customResultHandler}.</p>
   *
   * @return true if user decides to commit the changes, false if user presses Cancel.
   */
  public abstract boolean commitChanges(@NotNull Collection<? extends Change> changes, @NotNull LocalChangeList initialChangeList,
                                        @NotNull @NlsSafe String commitMessage, @Nullable CommitResultHandler customResultHandler);

  public abstract void loadAndShowCommittedChangesDetails(@NotNull Project project,
                                                          @NotNull VcsRevisionNumber revision,
                                                          @NotNull VirtualFile file,
                                                          @NotNull VcsKey key,
                                                          @Nullable RepositoryLocation location,
                                                          boolean local);

}
