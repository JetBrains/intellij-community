// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.CommitDialogChangesBrowser;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.impl.VcsRootIterator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.containers.UtilKt.isEmpty;
import static com.intellij.util.containers.UtilKt.notNullize;

public class ScheduleForAdditionAction extends AnAction implements DumbAware {

  @Override
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

  @Override
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
    return addUnversionedFilesToVcs(project, targetChangeList, files, unversionedFileCondition, changesConsumer);
  }

  @NotNull
  private Stream<VirtualFile> getUnversionedFiles(@NotNull AnActionEvent e, @NotNull Project project) {
    boolean hasExplicitUnversioned = !isEmpty(e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY));
    if (hasExplicitUnversioned) return e.getRequiredData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);

    if (!canHaveUnversionedFiles(e)) return Stream.empty();

    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    return notNullize(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM)).filter(file -> isFileUnversioned(file, vcsManager, changeListManager));
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
  protected boolean canHaveUnversionedFiles(@NotNull AnActionEvent e) {
    return ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES));
  }

  public static boolean addUnversionedFilesToVcs(@NotNull Project project,
                                                 @NotNull final LocalChangeList list,
                                                 @NotNull final List<VirtualFile> files,
                                                 @NotNull final Condition<? super FileStatus> statusChecker,
                                                 @Nullable Consumer<? super List<Change>> changesConsumer) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    final List<VcsException> exceptions = new ArrayList<>();
    final Set<VirtualFile> allProcessedFiles = new HashSet<>();
    ChangesUtil.processVirtualFilesByVcs(project, files, (vcs, items) -> {
      final CheckinEnvironment environment = vcs.getCheckinEnvironment();
      if (environment != null) {
        Set<VirtualFile> descendants = getUnversionedDescendantsRecursively(project, items, statusChecker);
        Set<VirtualFile> parents = getUnversionedParents(project, vcs, items, statusChecker);

        // it is assumed that not-added parents of files passed to scheduleUnversionedFilesForAddition() will also be added to vcs
        // (inside the method) - so common add logic just needs to refresh statuses of parents
        final List<VcsException> result = ContainerUtil.newArrayList();
        ProgressManager.getInstance().run(new Task.Modal(project, "Adding Files to VCS...", true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            indicator.setIndeterminate(true);
            List<VcsException> exs = environment.scheduleUnversionedFilesForAddition(ContainerUtil.newArrayList(descendants));
            if (exs != null) {
              ContainerUtil.addAll(result, exs);
            }
          }
        });

        allProcessedFiles.addAll(descendants);
        allProcessedFiles.addAll(parents);
        exceptions.addAll(result);
      }
    });

    if (!exceptions.isEmpty()) {
      StringBuilder message = new StringBuilder(VcsBundle.message("error.adding.files.prompt"));
      for (VcsException ex : exceptions) {
        message.append("\n").append(ex.getMessage());
      }
      Messages.showErrorDialog(project, message.toString(), VcsBundle.message("error.adding.files.title"));
    }

    FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
    for (VirtualFile file : allProcessedFiles) {
      fileStatusManager.fileStatusChanged(file);
    }
    VcsDirtyScopeManager.getInstance(project).filesDirty(allProcessedFiles, null);

    final boolean moveRequired = !list.isDefault();
    boolean syncUpdateRequired = changesConsumer != null;

    if (moveRequired || syncUpdateRequired) {
      // find the changes for the added files and move them to the necessary changelist
      InvokeAfterUpdateMode updateMode =
        syncUpdateRequired ? InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE : InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE;

      changeListManager.invokeAfterUpdate(() -> {
        List<Change> newChanges = ContainerUtil.filter(changeListManager.getDefaultChangeList().getChanges(), change -> {
          FilePath path = ChangesUtil.getAfterPath(change);
          return path != null && allProcessedFiles.contains(path.getVirtualFile());
        });

        if (moveRequired && !newChanges.isEmpty()) {
          changeListManager.moveChangesTo(list, newChanges.toArray(new Change[0]));
        }

        ChangesViewManager.getInstance(project).scheduleRefresh();

        if (changesConsumer != null) {
          changesConsumer.consume(newChanges);
        }
      }, updateMode, VcsBundle.message("change.lists.manager.add.unversioned"), null);
    }
    else {
      ChangesViewManager.getInstance(project).scheduleRefresh();
    }

    return exceptions.isEmpty();
  }

  @NotNull
  private static Set<VirtualFile> getUnversionedDescendantsRecursively(@NotNull Project project,
                                                                       @NotNull List<? extends VirtualFile> items,
                                                                       @NotNull Condition<? super FileStatus> condition) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final Set<VirtualFile> result = ContainerUtil.newHashSet();
    Processor<VirtualFile> addToResultProcessor = file -> {
      if (condition.value(changeListManager.getStatus(file))) {
        result.add(file);
      }
      return true;
    };

    for (VirtualFile item : items) {
      VcsRootIterator.iterateVfUnderVcsRoot(project, item, addToResultProcessor);
    }

    return result;
  }

  @NotNull
  private static Set<VirtualFile> getUnversionedParents(@NotNull Project project,
                                                        @NotNull AbstractVcs vcs,
                                                        @NotNull Collection<? extends VirtualFile> items,
                                                        @NotNull Condition<? super FileStatus> condition) {
    if (!vcs.areDirectoriesVersionedItems()) return Collections.emptySet();

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    HashSet<VirtualFile> result = ContainerUtil.newHashSet();

    for (VirtualFile item : items) {
      VirtualFile parent = item.getParent();

      while (parent != null && condition.value(changeListManager.getStatus(parent))) {
        result.add(parent);
        parent = parent.getParent();
      }
    }

    return result;
  }
}
