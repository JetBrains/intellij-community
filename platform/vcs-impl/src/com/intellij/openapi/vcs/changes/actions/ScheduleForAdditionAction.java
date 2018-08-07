// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.CommitDialogChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.containers.UtilKt.isEmpty;
import static com.intellij.util.containers.UtilKt.notNullize;

public class ScheduleForAdditionAction extends AnAction implements DumbAware {

  public void update(@NotNull AnActionEvent e) {
    boolean enabled = e.getProject() != null && !isEmpty(getUnversionedFiles(e, e.getProject()));

    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION.equals(e.getPlace()) ||
        ActionPlaces.CHANGES_VIEW_POPUP.equals(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }

    if (e.isFromActionToolbar() && e.getPresentation().getIcon() == null) {
      e.getPresentation().setIcon(IconUtil.getAddIcon());
    }
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    List<VirtualFile> unversionedFiles = getUnversionedFiles(e, project).collect(Collectors.toList());

    addUnversioned(project, unversionedFiles, this::isStatusForAddition, e.getData(ChangesBrowserBase.DATA_KEY));
  }

  public static boolean addUnversioned(@NotNull Project project,
                                       @NotNull List<VirtualFile> files,
                                       @NotNull Condition<FileStatus> unversionedFileCondition,
                                       @Nullable ChangesBrowserBase browser) {
    LocalChangeList targetChangeList = null;
    Consumer<List<Change>> changeConsumer = null;

    if (browser instanceof CommitDialogChangesBrowser) {
      targetChangeList = ((CommitDialogChangesBrowser)browser).getSelectedChangeList();
    }

    if (browser != null) {
      changeConsumer = changes -> browser.getViewer().includeChanges(changes);
    }

    return addUnversioned(project, files, targetChangeList, changeConsumer, unversionedFileCondition);
  }

  private static boolean addUnversioned(@NotNull Project project,
                                        @NotNull List<VirtualFile> files,
                                        @Nullable LocalChangeList targetChangeList,
                                        @Nullable Consumer<List<Change>> changesConsumer,
                                        @NotNull Condition<FileStatus> unversionedFileCondition) {
    if (files.isEmpty()) return true;

    ChangeListManagerImpl manager = ChangeListManagerImpl.getInstanceImpl(project);
    if (targetChangeList == null) targetChangeList = manager.getDefaultChangeList();

    FileDocumentManager.getInstance().saveAllDocuments();
    List<VcsException> exceptions = manager.addUnversionedFiles(targetChangeList, files, unversionedFileCondition, changesConsumer);
    return exceptions.isEmpty();
  }

  @NotNull
  private Stream<VirtualFile> getUnversionedFiles(@NotNull AnActionEvent e, @NotNull Project project) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    boolean hasExplicitUnversioned = !isEmpty(e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY));

    return hasExplicitUnversioned
           ? e.getRequiredData(ChangesListView.UNVERSIONED_FILES_DATA_KEY)
           : checkVirtualFiles(e)
             ? notNullize(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM)).filter(file -> isFileUnversioned(file, vcsManager, changeListManager))
             : Stream.empty();
  }

  private boolean isFileUnversioned(@NotNull VirtualFile file,
                                    @NotNull ProjectLevelVcsManager vcsManager,
                                    @NotNull ChangeListManager changeListManager) {
    AbstractVcs vcs = vcsManager.getVcsFor(file);
    return vcs != null && !vcs.areDirectoriesVersionedItems() && file.isDirectory() ||
           isStatusForAddition(changeListManager.getStatus(file));
  }

  protected boolean isStatusForAddition(FileStatus status) {
    return status == FileStatus.UNKNOWN;
  }

  /**
   * {@link #isStatusForAddition(FileStatus)} checks file status to be {@link FileStatus#UNKNOWN} (if not overridden).
   * As an optimization, we assume that if {@link ChangesListView#UNVERSIONED_FILES_DATA_KEY} is empty, but {@link VcsDataKeys#CHANGES} is
   * not, then there will be either versioned (files from changes, hijacked files, locked files, switched files) or ignored files in
   * {@link VcsDataKeys#VIRTUAL_FILE_STREAM}. So there will be no files with {@link FileStatus#UNKNOWN} status and we should not explicitly
   * check {@link VcsDataKeys#VIRTUAL_FILE_STREAM} files in this case.
   */
  protected boolean checkVirtualFiles(@NotNull AnActionEvent e) {
    return ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES));
  }
}
