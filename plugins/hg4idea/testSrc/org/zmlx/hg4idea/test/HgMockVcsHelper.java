// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.test;

import com.intellij.ide.errorTreeView.HotfixData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Substitutes AbstractVcsHelperImpl for tests, where dialogs need to be tested.
 * Currently it's just a stub implementation notifying listeners about action invoked (which would mean than a dialog would have been
 * shown during normal execution).
 * @author Kirill Likhodedov
 */
public final class HgMockVcsHelper extends AbstractVcsHelper {
  private final Collection<VcsHelperListener> myListeners = new HashSet<>();

  public HgMockVcsHelper(@NotNull Project project) {
    super(project);
  }

  @Override
  public void showErrors(List<? extends VcsException> abstractVcsExceptions, @NotNull String tabDisplayName) {
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
  public void showChangesListBrowser(@NotNull CommittedChangeList changelist, @Nullable @Nls String title) {
  }

  @Override
  public void showWhatDiffersBrowser(@NotNull Collection<Change> changes, @Nullable @Nls String title) {
  }

  @Override
  public void showCommittedChangesBrowser(@NotNull CommittedChangesProvider provider,
                                          @NotNull RepositoryLocation location,
                                          @Nullable @Nls String title,
                                          @Nullable Component parent) {
  }

  @Override
  public void openCommittedChangesTab(@NotNull CommittedChangesProvider provider,
                                      @NotNull RepositoryLocation location,
                                      @NotNull ChangeBrowserSettings settings,
                                      int maxCount,
                                      @Nullable String title) {
  }

  @NotNull
  @Override
  public List<VirtualFile> showMergeDialog(List<? extends VirtualFile> files,
                                           MergeProvider provider,
                                           @NotNull MergeDialogCustomizer mergeDialogCustomizer) {
    return ContainerUtil.emptyList();
  }

  @Override
  public void showFileHistory(@NotNull VcsHistoryProvider historyProvider, @NotNull FilePath path, @NotNull AbstractVcs vcs) {
  }

  @Override
  public void showFileHistory(@NotNull VcsHistoryProvider historyProvider,
                              AnnotationProvider annotationProvider,
                              @NotNull FilePath path,
                              @NotNull AbstractVcs vcs) {
  }

  @Nullable
  @Override
  public Collection<VirtualFile> selectFilesToProcess(List<? extends VirtualFile> files,
                                                      String title,
                                                      @Nullable String prompt,
                                                      String singleFileTitle,
                                                      String singleFilePromptTemplate,
                                                      @NotNull VcsShowConfirmationOption confirmationOption) {
    notifyListeners();
    return null;
  }

  @Nullable
  @Override
  public Collection<FilePath> selectFilePathsToProcess(@NotNull List<? extends FilePath> files,
                                                       String title,
                                                       @Nullable String prompt,
                                                       String singleFileTitle,
                                                       String singleFilePromptTemplate,
                                                       @NotNull VcsShowConfirmationOption confirmationOption) {
    notifyListeners();
    return null;
  }

  @Nullable
  @Override
  public Collection<FilePath> selectFilePathsToProcess(@NotNull List<? extends FilePath> files,
                                                       String title,
                                                       @Nullable String prompt,
                                                       @Nullable String singleFileTitle,
                                                       @Nullable String singleFilePromptTemplate,
                                                       @NotNull VcsShowConfirmationOption confirmationOption,
                                                       @Nullable String okActionName,
                                                       @Nullable String cancelActionName) {
    notifyListeners();
    return null;
  }

  @Override
  public boolean commitChanges(@NotNull Collection<? extends Change> changes, @NotNull LocalChangeList initialChangeList,
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
