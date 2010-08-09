/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Component which provides means to invoke different VCS-related services.
 */
public abstract class AbstractVcsHelper {
  public static AbstractVcsHelper getInstance(Project project) {
    return ServiceManager.getService(project, AbstractVcsHelper.class);
  }

  public static AbstractVcsHelper getInstanceChecked(final Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<AbstractVcsHelper>() {
      public AbstractVcsHelper compute() {
        if (project.isDisposed()) throw new ProcessCanceledException();
        return ServiceManager.getService(project, AbstractVcsHelper.class);
      }
    });
  }

  public abstract void showErrors(List<VcsException> abstractVcsExceptions, @NotNull String tabDisplayName);

  public abstract void showErrors(Map<HotfixData, List<VcsException>> exceptionGroups, @NotNull String tabDisplayName);

  /**
   * Runs the runnable inside the vcs transaction (if needed), collects all exceptions, commits/rollbacks transaction
   * and returns all exceptions together.
   */
  public abstract List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters);

  public void showError(final VcsException e, final String s) {
    showErrors(Arrays.asList(e), s);
  }

  public abstract void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs);

  public abstract void showDifferences(final VcsFileRevision cvsVersionOn, final VcsFileRevision cvsVersionOn1, final File file);

  public abstract void showChangesListBrowser(CommittedChangeList changelist, @Nls String title);

  public abstract void showChangesBrowser(List<CommittedChangeList> changelists);

  public abstract void showChangesBrowser(List<CommittedChangeList> changelists, @Nls String title);

  public abstract void showChangesBrowser(CommittedChangesProvider provider,
                                          final RepositoryLocation location,
                                          @Nls String title,
                                          @Nullable final Component parent);

  public abstract void showWhatDiffersBrowser(@Nullable Component parent, Collection<Change> changes, @Nls String title);

  @Nullable
  public abstract <T extends CommittedChangeList, U extends ChangeBrowserSettings> T chooseCommittedChangeList(CommittedChangesProvider<T, U> provider,
                                                                                                               RepositoryLocation location);

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

  @NotNull
  public abstract List<VirtualFile> showMergeDialog(List<VirtualFile> files, MergeProvider provider);

  /**
   * Shows the multiple file merge dialog for resolving conflicts in the specified set of virtual files.
   * Assumes all files are under the same VCS.
   *
   * @param files the files to show in the merge dialog.
   * @return the files for which the merge was actually performed.
   */
  @NotNull
  public abstract List<VirtualFile> showMergeDialog(List<VirtualFile> files);

  public abstract void showFileHistory(VcsHistoryProvider vcsHistoryProvider, FilePath path, final AbstractVcs vcs,
                                       final String repositoryPath);

  public abstract void showFileHistory(VcsHistoryProvider vcsHistoryProvider, AnnotationProvider annotationProvider, FilePath path,
                                       final String repositoryPath, final AbstractVcs vcs);

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
}
