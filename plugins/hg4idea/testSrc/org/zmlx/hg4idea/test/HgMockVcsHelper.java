/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.test;

import com.intellij.ide.errorTreeView.HotfixData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitResultHandler;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Substitutes AbstractVcsHelperImpl for tests, where dialogs need to be tested.
 * Currently it's just a stub implementation notifying listeners about action invoked (which would mean than a dialog would have been
 * shown during normal execution).
 * @author Kirill Likhodedov
 */
public class HgMockVcsHelper extends AbstractVcsHelper {

  private Collection<VcsHelperListener> myListeners = new THashSet<>();

  public HgMockVcsHelper(@NotNull Project project) {
    super(project);
  }

  @Override
  public void showErrors(List<VcsException> abstractVcsExceptions, @NotNull String tabDisplayName) {
  }

  @Override
  public void showErrors(Map<HotfixData, List<VcsException>> exceptionGroups, @NotNull String tabDisplayName) {
  }

  @Override
  public List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters) {
    return null;
  }

  @Override
  public void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs, int line) {
  }

  @Override
  public void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs) {
  }

  @Override
  public void showDifferences(VcsFileRevision cvsVersionOn, VcsFileRevision cvsVersionOn1, File file) {
  }

  @Override
  public void showChangesListBrowser(CommittedChangeList changelist, @Nls String title) {
  }

  @Override
  public void showChangesBrowser(List<CommittedChangeList> changelists) {
  }

  @Override
  public void showChangesBrowser(List<CommittedChangeList> changelists, @Nls String title) {
  }

  @Override
  public void showChangesBrowser(CommittedChangesProvider provider,
                                 RepositoryLocation location,
                                 @Nls String title,
                                 @Nullable Component parent) {
  }

  @Override
  public void showWhatDiffersBrowser(@Nullable Component parent, Collection<Change> changes, @Nls String title) {
  }

  @Override
  public <T extends CommittedChangeList, U extends ChangeBrowserSettings> T chooseCommittedChangeList(@NotNull CommittedChangesProvider<T, U> provider,
                                                                                                      RepositoryLocation location) {
    return null;
  }

  @Override
  public void openCommittedChangesTab(AbstractVcs vcs, VirtualFile root, ChangeBrowserSettings settings, int maxCount, String title) {
  }

  @Override
  public void openCommittedChangesTab(CommittedChangesProvider provider,
                                      RepositoryLocation location,
                                      ChangeBrowserSettings settings,
                                      int maxCount,
                                      String title) {
  }

  @NotNull
  @Override
  public List<VirtualFile> showMergeDialog(List<VirtualFile> files, MergeProvider provider, @NotNull MergeDialogCustomizer mergeDialogCustomizer) {
    return null;
  }

  @Override
  public void showFileHistory(@NotNull VcsHistoryProvider historyProvider, @NotNull FilePath path, @NotNull AbstractVcs vcs, String repositoryPath) {
  }

  @Override
  public void showFileHistory(@NotNull VcsHistoryProvider historyProvider,
                              AnnotationProvider annotationProvider,
                              @NotNull FilePath path,
                              String repositoryPath,
                              @NotNull AbstractVcs vcs) {
  }

  @Override
  public void showRollbackChangesDialog(List<Change> changes) {
  }

  @Nullable
  @Override
  public Collection<VirtualFile> selectFilesToProcess(List<VirtualFile> files,
                                                      String title,
                                                      @Nullable String prompt,
                                                      String singleFileTitle,
                                                      String singleFilePromptTemplate,
                                                      VcsShowConfirmationOption confirmationOption) {
    notifyListeners();
    return null;
  }
  
  @Nullable
  @Override
  public Collection<FilePath> selectFilePathsToProcess(List<FilePath> files,
                                                       String title,
                                                       @Nullable String prompt,
                                                       String singleFileTitle,
                                                       String singleFilePromptTemplate,
                                                       VcsShowConfirmationOption confirmationOption) {
    notifyListeners();
    return null;
  }

  @Override
  public boolean commitChanges(@NotNull Collection<Change> changes, @NotNull LocalChangeList initialChangeList,
                               @NotNull String commitMessage, @Nullable CommitResultHandler customResultHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void loadAndShowCommittedChangesDetails(@NotNull Project project,
                                                 @NotNull VcsRevisionNumber revision,
                                                 @NotNull VirtualFile file,
                                                 @NotNull VcsKey key,
                                                 @Nullable RepositoryLocation location,
                                                 boolean local) {

  }

  public void addListener(VcsHelperListener listener) {
    myListeners.add(listener);
  }

  private void notifyListeners() {
    for (VcsHelperListener listener : myListeners) {
      listener.dialogInvoked();
    }
  }

}
