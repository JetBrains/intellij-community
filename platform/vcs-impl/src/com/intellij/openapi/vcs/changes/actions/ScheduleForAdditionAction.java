// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.CommitDialogChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.progress.util.BackgroundTaskUtil.computeWithModalProgress;
import static com.intellij.util.containers.UtilKt.isEmpty;
import static com.intellij.util.containers.UtilKt.notNullize;

public class ScheduleForAdditionAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = isEnabled(e);

    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION.equals(e.getPlace()) ||
        ActionPlaces.CHANGES_VIEW_POPUP.equals(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }

    if (e.isFromActionToolbar() && e.getPresentation().getIcon() == null) {
      e.getPresentation().setIcon(IconUtil.getAddIcon());
    }
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getProject() != null && !isEmpty(getUnversionedFiles(e, e.getProject()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    List<VirtualFile> unversionedFiles = getUnversionedFiles(e, project).collect(Collectors.toList());

    addUnversioned(project, unversionedFiles, e.getData(ChangesBrowserBase.DATA_KEY));
  }

  public static boolean addUnversioned(@NotNull Project project,
                                       @NotNull List<VirtualFile> files,
                                       @Nullable ChangesBrowserBase browser) {
    LocalChangeList targetChangeList = null;
    Consumer<List<Change>> changeConsumer = null;

    if (browser instanceof CommitDialogChangesBrowser) {
      targetChangeList = ((CommitDialogChangesBrowser)browser).getSelectedChangeList();
    }

    if (browser != null) {
      changeConsumer = changes -> browser.getViewer().includeChanges(changes);
    }

    return addUnversionedFiles(project, targetChangeList, files, changeConsumer);
  }

  public static void addUnversionedFiles(@NotNull Project project,
                                         @NotNull LocalChangeList list,
                                         @NotNull List<VirtualFile> files) {
    addUnversionedFiles(project, list, files, null);
  }

  private static boolean addUnversionedFiles(@NotNull Project project,
                                             @Nullable LocalChangeList targetChangeList,
                                             @NotNull List<VirtualFile> files,
                                             @Nullable Consumer<? super List<Change>> changesConsumer) {
    if (files.isEmpty()) return true;

    ChangeListManagerImpl manager = ChangeListManagerImpl.getInstanceImpl(project);
    FileDocumentManager.getInstance().saveAllDocuments();

    List<VcsException> exceptions = new ArrayList<>();
    Set<VirtualFile> allProcessedFiles = computeWithModalProgress(project, "Adding Files to VCS...", true, (indicator) -> {
      return manager.addUnversionedToVcs(files, exceptions);
    });

    if (!exceptions.isEmpty()) {
      StringBuilder message = new StringBuilder(VcsBundle.message("error.adding.files.prompt"));
      for (VcsException ex : exceptions) {
        message.append("\n").append(ex.getMessage());
      }
      Messages.showErrorDialog(project, message.toString(), VcsBundle.message("error.adding.files.title"));
    }

    boolean moveRequired = targetChangeList != null && !targetChangeList.isDefault();
    boolean syncUpdateRequired = changesConsumer != null;

    if (moveRequired || syncUpdateRequired) {
      manager.invokeAfterUpdate(() -> {
        List<Change> newChanges = ContainerUtil.filter(manager.getDefaultChangeList().getChanges(), change -> {
          return allProcessedFiles.contains(change.getVirtualFile());
        });

        if (moveRequired && !newChanges.isEmpty()) {
          manager.moveChangesTo(targetChangeList, newChanges.toArray(new Change[0]));
        }

        if (changesConsumer != null) {
          changesConsumer.consume(newChanges);
        }
      }, InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE, VcsBundle.message("change.lists.manager.add.unversioned"), null);
    }

    return exceptions.isEmpty();
  }

  @NotNull
  protected static Stream<VirtualFile> getUnversionedFiles(@NotNull AnActionEvent e, @NotNull Project project) {
    boolean hasExplicitUnversioned = !isEmpty(e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY));
    if (hasExplicitUnversioned) return e.getRequiredData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);

    if (!canHaveUnversionedFiles(e)) return Stream.empty();

    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    return notNullize(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM)).filter(file -> isFileUnversioned(file, vcsManager, changeListManager));
  }

  private static boolean isFileUnversioned(@NotNull VirtualFile file,
                                           @NotNull ProjectLevelVcsManager vcsManager,
                                           @NotNull ChangeListManager changeListManager) {
    AbstractVcs vcs = vcsManager.getVcsFor(file);
    return vcs != null && !vcs.areDirectoriesVersionedItems() && file.isDirectory() ||
           changeListManager.getStatus(file) == FileStatus.UNKNOWN;
  }

  /**
   * {@link #isFileUnversioned} checks file status to be {@link FileStatus#UNKNOWN}.
   * As an optimization, we assume that if {@link ChangesListView#UNVERSIONED_FILES_DATA_KEY} is empty, but {@link VcsDataKeys#CHANGES} is
   * not, then there will be either versioned (files from changes, hijacked files, locked files, switched files) or ignored files in
   * {@link VcsDataKeys#VIRTUAL_FILE_STREAM}. So there will be no files with {@link FileStatus#UNKNOWN} status and we should not explicitly
   * check {@link VcsDataKeys#VIRTUAL_FILE_STREAM} files in this case.
   */
  private static boolean canHaveUnversionedFiles(@NotNull AnActionEvent e) {
    return ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES));
  }
}
