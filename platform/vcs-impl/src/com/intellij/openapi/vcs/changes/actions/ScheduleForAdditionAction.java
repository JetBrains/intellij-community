// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.CommitDialogChangesBrowser;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.impl.VcsRootIterator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.containers.UtilKt.isEmpty;
import static com.intellij.util.containers.UtilKt.notNullize;
import static kotlin.collections.CollectionsKt.intersect;

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

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    List<VirtualFile> unversionedFiles = getUnversionedFiles(e, project).collect(Collectors.toList());

    addUnversioned(project, unversionedFiles, e.getData(ChangesBrowserBase.DATA_KEY));
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getProject() != null && !isEmpty(getUnversionedFiles(e, e.getProject()));
  }

  public static boolean addUnversioned(@NotNull Project project,
                                       @NotNull List<? extends VirtualFile> files,
                                       @Nullable ChangesBrowserBase browser) {
    return addUnversioned(project, files, browser, null);
  }

  protected static boolean addUnversioned(@NotNull Project project,
                                          @NotNull List<? extends VirtualFile> files,
                                          @Nullable ChangesBrowserBase browser,
                                          @Nullable PairConsumer<? super ProgressIndicator, ? super List<VcsException>> additionalTask) {
    if (files.isEmpty() && additionalTask == null) return true;

    LocalChangeList targetChangeList = browser instanceof CommitDialogChangesBrowser
                                       ? ((CommitDialogChangesBrowser)browser).getSelectedChangeList()
                                       : ChangeListManager.getInstance(project).getDefaultChangeList();

    Consumer<List<Change>> changeConsumer = browser != null ? changes -> browser.getViewer().includeChanges(changes) : null;

    FileDocumentManager.getInstance().saveAllDocuments();
    return addUnversionedFilesToVcs(project, targetChangeList, files, changeConsumer, additionalTask);
  }

  @NotNull
  public static Stream<VirtualFile> getUnversionedFiles(@NotNull AnActionEvent e, @NotNull Project project) {
    boolean hasExplicitUnversioned = !isEmpty(e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY));
    if (hasExplicitUnversioned) return e.getRequiredData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);

    // As an optimization, we assume that if {@link ChangesListView#UNVERSIONED_FILES_DATA_KEY} is empty, but {@link VcsDataKeys#CHANGES} is
    // not, then there will be either versioned (files from changes, hijacked files, locked files, switched files) or ignored files in
    // {@link VcsDataKeys#VIRTUAL_FILE_STREAM}. So there will be no files with {@link FileStatus#UNKNOWN} status and we should not explicitly
    // check {@link VcsDataKeys#VIRTUAL_FILE_STREAM} files in this case.
    if (!ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES))) return Stream.empty();

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

  public static boolean addUnversionedFilesToVcs(@NotNull Project project,
                                                 @NotNull LocalChangeList list,
                                                 @NotNull List<? extends VirtualFile> files) {
    return addUnversionedFilesToVcs(project, list, files, null, null);
  }

  public static boolean addUnversionedFilesToVcs(@NotNull Project project,
                                                 @NotNull LocalChangeList list,
                                                 @NotNull List<? extends VirtualFile> files,
                                                 @Nullable Consumer<? super List<Change>> changesConsumer,
                                                 @Nullable PairConsumer<? super ProgressIndicator, ? super List<VcsException>> additionalTask) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    final List<VcsException> exceptions = new ArrayList<>();
    final Set<VirtualFile> allProcessedFiles = new HashSet<>();

    ProgressManager.getInstance().run(new Task.Modal(project, "Adding Files to VCS...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        ChangesUtil.processVirtualFilesByVcs(project, files, (vcs, files) -> addUnversionedFilesToVcs(project, vcs, files, allProcessedFiles, exceptions));

        if (additionalTask != null) additionalTask.consume(indicator, exceptions);
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

    boolean moveRequired = !list.isDefault() && !allProcessedFiles.isEmpty();
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
          if (moveRequired && !newChanges.isEmpty()) {
            // newChanges contains ChangeListChange instances from active change list in case of partial changes
            // so we obtain necessary changes again from required change list to pass to callback
            LocalChangeList newList = changeListManager.getChangeList(list.getId());
            if (newList != null) newChanges = new ArrayList<>(intersect(newList.getChanges(), newChanges));
          }

          changesConsumer.consume(newChanges);
        }
      }, updateMode, VcsBundle.message("change.lists.manager.add.unversioned"), null);
    }
    else {
      ChangesViewManager.getInstance(project).scheduleRefresh();
    }

    return exceptions.isEmpty();
  }

  private static void addUnversionedFilesToVcs(@NotNull Project project,
                                               @NotNull AbstractVcs vcs,
                                               @NotNull List<? extends VirtualFile> items,
                                               @NotNull Set<? super VirtualFile> allProcessedFiles,
                                               @NotNull List<? super VcsException> exceptions) {
    CheckinEnvironment environment = vcs.getCheckinEnvironment();
    if (environment == null) return;

    Set<VirtualFile> descendants = ReadAction.compute(() -> getUnversionedDescendantsRecursively(project, items));
    Set<VirtualFile> parents = ReadAction.compute(() -> getUnversionedParents(project, vcs, items));

    // it is assumed that not-added parents of files passed to scheduleUnversionedFilesForAddition() will also be added to vcs
    // (inside the method) - so common add logic just needs to refresh statuses of parents
    List<VcsException> exs = environment.scheduleUnversionedFilesForAddition(new ArrayList<>(descendants));
    if (exs != null) exceptions.addAll(exs);

    allProcessedFiles.addAll(descendants);
    allProcessedFiles.addAll(parents);
  }

  @NotNull
  private static Set<VirtualFile> getUnversionedDescendantsRecursively(@NotNull Project project,
                                                                       @NotNull List<? extends VirtualFile> items) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final Set<VirtualFile> result = new HashSet<>();
    Processor<VirtualFile> addToResultProcessor = file -> {
      if (changeListManager.getStatus(file) == FileStatus.UNKNOWN) {
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
                                                        @NotNull Collection<? extends VirtualFile> items) {
    if (!vcs.areDirectoriesVersionedItems()) return Collections.emptySet();

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    HashSet<VirtualFile> result = new HashSet<>();

    for (VirtualFile item : items) {
      VirtualFile parent = item.getParent();

      while (parent != null && changeListManager.getStatus(parent) == FileStatus.UNKNOWN) {
        result.add(parent);
        parent = parent.getParent();
      }
    }

    return result;
  }
}
