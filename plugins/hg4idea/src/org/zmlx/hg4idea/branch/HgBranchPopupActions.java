// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.branch;

import com.intellij.CommonBundle;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.dvcs.ui.NewBranchAction;
import com.intellij.dvcs.ui.PopupElementWithAdditionalInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import icons.DvcsImplIcons;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgDisposable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgBookmarkCommand;
import org.zmlx.hg4idea.command.HgBranchCreateCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.commit.HgCloseBranchExecutor;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.ui.HgBookmarkDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.*;

import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.dvcs.ui.BranchActionUtil.FAVORITE_BRANCH_COMPARATOR;
import static com.intellij.dvcs.ui.BranchActionUtil.getNumOfTopShownBranches;
import static org.zmlx.hg4idea.HgNotificationIdsHolder.BRANCH_CREATION_ERROR;
import static org.zmlx.hg4idea.util.HgUtil.getNewBranchNameFromUser;
import static org.zmlx.hg4idea.util.HgUtil.getSortedNamesWithoutHashes;

public final class HgBranchPopupActions {
  private final @NotNull Project myProject;
  private final @NotNull HgRepository myRepository;

  HgBranchPopupActions(@NotNull Project project, @NotNull HgRepository repository) {
    myProject = project;
    myRepository = repository;
  }

  ActionGroup createActions() {
    return createActions(null, null, false);
  }

  ActionGroup createActions(@Nullable LightActionGroup toInsert, @Nullable HgRepository specificRepository, boolean firstLevelGroup) {
    LightActionGroup popupGroup = new LightActionGroup(false);
    popupGroup.addAction(new HgNewBranchAction(myProject, Collections.singletonList(myRepository), myRepository));
    popupGroup.addAction(new HgNewBookmarkAction(Collections.singletonList(myRepository), myRepository));
    popupGroup.addAction(new HgBranchPopupActions.HgCloseBranchAction(Collections.singletonList(myRepository), myRepository));
    popupGroup.addAction(new HgShowUnnamedHeadsForCurrentBranchAction(myRepository));
    if (toInsert != null) {
      popupGroup.addAll(toInsert);
    }

    popupGroup.addSeparator(specificRepository == null ?
                            HgBundle.message("hg4idea.branch.bookmarks") :
                            HgBundle.message("hg4idea.branch.bookmarks.in.repo", DvcsUtil.getShortRepositoryName(specificRepository)));
    String currentBookmark = myRepository.getCurrentBookmark();
    List<BookmarkActions> bookmarkActions = StreamEx.of(getSortedNamesWithoutHashes(myRepository.getBookmarks()))
      .filter(bm -> !bm.equals(currentBookmark))
      .map(bm -> new BookmarkActions(myProject, Collections.singletonList(myRepository), bm))
      .sorted(FAVORITE_BRANCH_COMPARATOR)
      .toList();
    int topShownBookmarks = getNumOfTopShownBranches(bookmarkActions);
    if (currentBookmark != null) {
      bookmarkActions = ContainerUtil.prepend(bookmarkActions, new CurrentActiveBookmark(myProject, Collections.singletonList(myRepository),
                                                                                         currentBookmark));
      topShownBookmarks++;
    }
    wrapWithMoreActionIfNeeded(myProject, popupGroup, bookmarkActions, topShownBookmarks,
                               firstLevelGroup ? HgBranchPopup.SHOW_ALL_BOOKMARKS_KEY : null, firstLevelGroup);

    //only opened branches have to be shown
    popupGroup.addSeparator(specificRepository == null ?
                            HgBundle.message("hg4idea.branch.branches.separator") :
                            HgBundle.message("hg4idea.branch.branches.in.repo.separator",
                                             DvcsUtil.getShortRepositoryName(specificRepository)));
    List<BranchActions> branchActions = StreamEx.of(myRepository.getOpenedBranches())
      .sorted(StringUtil::naturalCompare)
      .filter(b -> !b.equals(myRepository.getCurrentBranch()))
      .map(b -> new BranchActions(myProject, Collections.singletonList(myRepository), b))
      .sorted(FAVORITE_BRANCH_COMPARATOR)
      .prepend(new CurrentBranch(myProject, Collections.singletonList(myRepository), myRepository.getCurrentBranch()))
      .toList();
    wrapWithMoreActionIfNeeded(myProject, popupGroup, branchActions, getNumOfTopShownBranches(branchActions) + 1,
                               firstLevelGroup ? HgBranchPopup.SHOW_ALL_BRANCHES_KEY : null, firstLevelGroup);
    return popupGroup;
  }

  public static class HgNewBranchAction extends NewBranchAction<HgRepository> {
    final @NotNull HgRepository myPreselectedRepo;

    public HgNewBranchAction(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull HgRepository preselectedRepo) {
      super(project, repositories);
      myPreselectedRepo = preselectedRepo;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final String name = getNewBranchNameFromUser(myPreselectedRepo, HgBundle.message("hg4idea.branch.create"));
      if (name == null) {
        return;
      }
      new Task.Backgroundable(myProject, HgBundle.message("hg4idea.branch.creating.progress", myRepositories.size())) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          createNewBranchInCurrentThread(name);
        }
      }.queue();
    }

    public void createNewBranchInCurrentThread(final @NotNull String name) {
      for (final HgRepository repository : myRepositories) {
        try {
          HgCommandResult result = new HgBranchCreateCommand(myProject, repository.getRoot(), name).executeInCurrentThread();
          repository.update();
          if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
            new HgCommandResultNotifier(myProject)
              .notifyError(BRANCH_CREATION_ERROR,
                           result,
                           HgBundle.message("hg4idea.branch.creation.error"),
                           HgBundle.message("hg4idea.branch.creation.error.msg", name));
          }
        }
        catch (HgCommandException exception) {
          HgErrorUtil.handleException(myProject,
                                      BRANCH_CREATION_ERROR,
                                      HgBundle.message("hg4idea.branch.cannot.create"),
                                      exception);
        }
      }
    }
  }

  static final class HgCloseBranchAction extends DumbAwareAction {
    private final @NotNull List<HgRepository> myRepositories;
    final @NotNull HgRepository myPreselectedRepo;

    HgCloseBranchAction(@NotNull List<HgRepository> repositories, @NotNull HgRepository preselectedRepo) {
      super(HgBundle.messagePointer("action.hg4idea.branch.close", repositories.size()),
            HgBundle.messagePointer("action.hg4idea.branch.close.description", repositories.size()),
            AllIcons.Actions.Cancel);
      myRepositories = repositories;
      myPreselectedRepo = preselectedRepo;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = myPreselectedRepo.getProject();
      StoreUtil.saveDocumentsAndProjectSettings(project);
      ChangeListManager.getInstance(project).invokeAfterUpdateWithModal(
        true, VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message"),
        () -> commitAndCloseBranch(project));
    }

    private void commitAndCloseBranch(final @NotNull Project project) {
      final LocalChangeList activeChangeList = ChangeListManager.getInstance(project).getDefaultChangeList();
      HgVcs vcs = HgVcs.getInstance(project);
      assert vcs != null;
      final HgRepositoryManager repositoryManager = HgUtil.getRepositoryManager(project);
      List<Change> changesForRepositories = ContainerUtil.filter(activeChangeList.getChanges(),
                                                                 change -> myRepositories.contains(repositoryManager.getRepositoryForFile(
                                                                   ChangesUtil.getFilePath(change))));
      Set<AbstractVcs> affectedVcses = Collections.singleton(vcs);
      List<HgCloseBranchExecutor> executors = Collections.singletonList(new HgCloseBranchExecutor(myRepositories));
      String commitMessage = "Close Branch"; //NON-NLS
      CommitChangeListDialog.showCommitDialog(project, affectedVcses, changesForRepositories, activeChangeList,
                                              executors, false, commitMessage, null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(ContainerUtil.and(myRepositories,
                                                                 repository -> repository.getOpenedBranches()
                                                                   .contains(repository.getCurrentBranch())));
    }
  }

  static final class HgNewBookmarkAction extends DumbAwareAction {
    final @NotNull List<HgRepository> myRepositories;
    final @NotNull HgRepository myPreselectedRepo;

    HgNewBookmarkAction(@NotNull List<HgRepository> repositories, @NotNull HgRepository preselectedRepo) {
      super(HgBundle.messagePointer("action.hg4idea.bookmark.new"),
            HgBundle.messagePointer("action.hg4idea.bookmark.new.description"),
            AllIcons.General.Add);
      myRepositories = repositories;
      myPreselectedRepo = preselectedRepo;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      DvcsUtil.disableActionIfAnyRepositoryIsFresh(e, myRepositories, HgBundle.message("action.not.possible.in.fresh.repo.new.bookmark"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {

      final HgBookmarkDialog bookmarkDialog = new HgBookmarkDialog(myPreselectedRepo);
      if (bookmarkDialog.showAndGet()) {
        final String name = bookmarkDialog.getName();
        if (!StringUtil.isEmptyOrSpaces(name)) {
          HgBookmarkCommand.createBookmarkAsynchronously(myRepositories, name, bookmarkDialog.isActive());
        }
      }
    }
  }

  public static class HgShowUnnamedHeadsForCurrentBranchAction extends ActionGroup implements DumbAware {
    final @NotNull HgRepository myRepository;
    final @NotNull String myCurrentBranchName;
    final @NotNull Collection<Hash> myHeads;

    public HgShowUnnamedHeadsForCurrentBranchAction(@NotNull HgRepository repository) {
      super(Presentation.NULL_STRING, true);
      myRepository = repository;
      myCurrentBranchName = repository.getCurrentBranch();
      getTemplatePresentation().setText(HgBundle.message("action.hg4idea.show.unnamed.heads", myCurrentBranchName));
      myHeads = filterUnnamedHeads();
    }

    private @NotNull Collection<Hash> filterUnnamedHeads() {
      Collection<Hash> branchWithHashes = myRepository.getBranches().get(myCurrentBranchName);
      String currentHead = myRepository.getCurrentRevision();
      if (branchWithHashes == null || currentHead == null || myRepository.getState() != Repository.State.NORMAL) {
        // repository is fresh or branch is fresh or complex state
        return Collections.emptySet();
      }
      else {
        Collection<Hash> bookmarkHashes = ContainerUtil.map(myRepository.getBookmarks(), info -> info.getHash());
        branchWithHashes.removeAll(bookmarkHashes);
        branchWithHashes.remove(HashImpl.build(currentHead));
      }
      return branchWithHashes;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      List<AnAction> branchHeadActions = new ArrayList<>();
      for (Hash hash : myHeads) {
        branchHeadActions
          .add(new HgCommonBranchActions(myRepository.getProject(), Collections.singletonList(myRepository), hash.toShortString()));
      }
      return branchHeadActions.toArray(AnAction.EMPTY_ARRAY);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      if (myRepository.isFresh() || myHeads.isEmpty()) {
        e.getPresentation().setEnabledAndVisible(false);
      }
      else if (myRepository.getState() != Repository.State.NORMAL) {
        e.getPresentation().setEnabled(false);
      }
    }
  }

  static class BranchActions extends HgCommonBranchActions {
    BranchActions(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
      super(project, repositories, branchName, HgBranchType.BRANCH);
    }
  }

  static final class CurrentBranch extends BranchActions implements PopupElementWithAdditionalInfo {
    CurrentBranch(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
      super(project, repositories, branchName);
      setIcons(DvcsImplIcons.CurrentBranchFavoriteLabel, DvcsImplIcons.CurrentBranchLabel, AllIcons.Nodes.Favorite,
               AllIcons.Nodes.NotFavoriteOnHover);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return AnAction.EMPTY_ARRAY;
    }
  }

  /**
   * Actions available for  bookmarks.
   */
  static class BookmarkActions extends HgCommonBranchActions {
    BookmarkActions(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
      super(project, repositories, branchName, HgBranchType.BOOKMARK);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return ArrayUtil.append(super.getChildren(e), new DeleteBookmarkAction(myProject, myRepositories, myBranchName));
    }

    private static final class DeleteBookmarkAction extends HgBranchAbstractAction {
      DeleteBookmarkAction(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
        super(project, CommonBundle.messagePointer("button.delete"), repositories, branchName);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        BackgroundTaskUtil.executeOnPooledThread(HgDisposable.getInstance(myProject), () -> {
          for (HgRepository repository : myRepositories) {
            HgBookmarkCommand.deleteBookmarkSynchronously(myProject, repository.getRoot(), myBranchName);
          }
        });
      }
    }
  }

  static final class CurrentActiveBookmark extends BookmarkActions implements PopupElementWithAdditionalInfo {
    CurrentActiveBookmark(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
      super(project, repositories, branchName);
      setIcons(DvcsImplIcons.CurrentBranchFavoriteLabel, DvcsImplIcons.CurrentBranchLabel, AllIcons.Nodes.Favorite,
               AllIcons.Nodes.NotFavoriteOnHover);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{new BookmarkActions.DeleteBookmarkAction(myProject, myRepositories, myBranchName)};
    }
  }
}
