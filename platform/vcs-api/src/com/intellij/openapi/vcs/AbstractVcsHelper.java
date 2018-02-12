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
package com.intellij.openapi.vcs;

import com.intellij.ide.errorTreeView.HotfixData;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Component which provides means to invoke different VCS-related services.
 */
public abstract class AbstractVcsHelper {

  @NotNull protected final Project myProject;

  protected AbstractVcsHelper(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static AbstractVcsHelper getInstance(Project project) {
    return ServiceManager.getService(project, AbstractVcsHelper.class);
  }

  public abstract void showErrors(List<VcsException> abstractVcsExceptions, @NotNull String tabDisplayName);

  public abstract void showErrors(Map<HotfixData, List<VcsException>> exceptionGroups, @NotNull String tabDisplayName);

  /**
   * Runs the runnable inside the vcs transaction (if needed), collects all exceptions, commits/rollbacks transaction
   * and returns all exceptions together.
   */
  public abstract List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters);

  public void showError(final VcsException e, final String tabDisplayName) {
    showErrors(Arrays.asList(e), tabDisplayName);
  }

  public abstract void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs);

  public abstract void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs, int line);

  public abstract void showChangesListBrowser(CommittedChangeList changelist, @Nls String title);

  public void showChangesListBrowser(CommittedChangeList changelist, @Nullable VirtualFile toSelect, @Nls String title) {
    showChangesListBrowser(changelist, title);
  }

  public abstract void showChangesBrowser(List<CommittedChangeList> changelists);

  public abstract void showChangesBrowser(List<CommittedChangeList> changelists, @Nls String title);

  public abstract void showChangesBrowser(CommittedChangesProvider provider,
                                          final RepositoryLocation location,
                                          @Nls String title,
                                          @Nullable final Component parent);

  public abstract void showWhatDiffersBrowser(@Nullable Component parent, Collection<Change> changes, @Nls String title);

  public abstract void openCommittedChangesTab(AbstractVcs vcs,
                                               VirtualFile root,
                                               ChangeBrowserSettings settings,
                                               int maxCount,
                                               final String title);

  public abstract void openCommittedChangesTab(CommittedChangesProvider provider,
                                               RepositoryLocation location,
                                               ChangeBrowserSettings settings,
                                               int maxCount,
                                               final String title);

  /**
   * Shows the multiple file merge dialog for resolving conflicts in the specified set of virtual files.
   * Assumes all files are under the same VCS.
   *
   * @param files the files to show in the merge dialog.
   * @param provider MergeProvider to be used for merging.
   * @param mergeDialogCustomizer custom container of titles, descriptions and messages for the merge dialog.
   * @return changed files for which the merge was actually performed.
   */
  public abstract @NotNull List<VirtualFile> showMergeDialog(List<VirtualFile> files, MergeProvider provider, @NotNull MergeDialogCustomizer mergeDialogCustomizer);

  /**
   * {@link #showMergeDialog(java.util.List, com.intellij.openapi.vcs.merge.MergeProvider)} without description.
   */
  @NotNull
  public final List<VirtualFile> showMergeDialog(List<VirtualFile> files, MergeProvider provider) {
    return showMergeDialog(files, provider, new MergeDialogCustomizer());
  }

  /**
   * {@link #showMergeDialog(java.util.List, com.intellij.openapi.vcs.merge.MergeProvider)} without description and with default merge provider
   * for the current VCS.
   */
  @NotNull
  public final List<VirtualFile> showMergeDialog(List<VirtualFile> files) {
    if (files.isEmpty()) return Collections.emptyList();
    MergeProvider provider = null;
    for (VirtualFile virtualFile : files) {
      final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
      if (vcs != null) {
        provider = vcs.getMergeProvider();
        if (provider != null) break;
      }
    }
    if (provider == null) return Collections.emptyList();
    return showMergeDialog(files, provider);
  }

  public abstract void showFileHistory(@NotNull VcsHistoryProvider historyProvider, @NotNull FilePath path, @NotNull AbstractVcs vcs);

  public abstract void showFileHistory(@NotNull VcsHistoryProvider historyProvider,
                                       @Nullable AnnotationProvider annotationProvider,
                                       @NotNull FilePath path,
                                       @NotNull AbstractVcs vcs);
  
  /**
   * Shows the "Rollback Changes" dialog with the specified list of changes.
   *
   * @param changes the changes to show in the dialog.
   */
  public abstract void showRollbackChangesDialog(List<Change> changes);

  @Nullable
  public abstract Collection<VirtualFile> selectFilesToProcess(List<VirtualFile> files,
                                                               final String title,
                                                               @Nullable final String prompt,
                                                               final String singleFileTitle,
                                                               final String singleFilePromptTemplate,
                                                               final VcsShowConfirmationOption confirmationOption);

  @Nullable
  public abstract Collection<FilePath> selectFilePathsToProcess(List<FilePath> files,
                                                                final String title,
                                                                @Nullable final String prompt,
                                                                final String singleFileTitle,
                                                                final String singleFilePromptTemplate,
                                                                final VcsShowConfirmationOption confirmationOption);

  @Nullable
  public Collection<FilePath> selectFilePathsToProcess(List<FilePath> files,
                                                                final String title,
                                                                @Nullable final String prompt,
                                                                final String singleFileTitle,
                                                                final String singleFilePromptTemplate,
                                                                final VcsShowConfirmationOption confirmationOption,
                                                                @Nullable String okActionName,
                                                                @Nullable String cancelActionName) {
    return selectFilePathsToProcess(files, title, prompt, singleFileTitle, singleFilePromptTemplate, confirmationOption);
  };
  
  
  /**
   * <p>Shows commit dialog, fills it with the given changes and given commit message, initially selects the given changelist.</p>
   * <p>Note that the method is asynchronous: it returns right after user presses "Commit" or "Cancel" and after all pre-commit handlers
   *    have been called. It doesn't wait for commit itself to succeed or fail - for this use the {@code customResultHandler}.</p>
   * @return true if user decides to commit the changes, false if user presses Cancel.
   */
  public abstract boolean commitChanges(@NotNull Collection<Change> changes, @NotNull LocalChangeList initialChangeList,
                               @NotNull String commitMessage, @Nullable CommitResultHandler customResultHandler);

  public abstract void loadAndShowCommittedChangesDetails(@NotNull Project project,
                                                          @NotNull VcsRevisionNumber revision,
                                                          @NotNull VirtualFile file,
                                                          @NotNull VcsKey key,
                                                          @Nullable RepositoryLocation location,
                                                          boolean local);

}
