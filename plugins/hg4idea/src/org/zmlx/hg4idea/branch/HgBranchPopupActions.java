// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.branch;

import com.intellij.CommonBundle;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.dvcs.ui.NewBranchAction;
import com.intellij.dvcs.ui.PopupElementWithAdditionalInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.dvcs.ui.BranchActionUtil.FAVORITE_BRANCH_COMPARATOR;
import static com.intellij.dvcs.ui.BranchActionUtil.getNumOfTopShownBranches;
import static org.zmlx.hg4idea.util.HgUtil.getNewBranchNameFromUser;
import static org.zmlx.hg4idea.util.HgUtil.getSortedNamesWithoutHashes;

public class HgBranchPopupActions {

  @NotNull private final Project myProject;
  @NotNull private final HgRepository myRepository;

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
      bookmarkActions.add(0, new CurrentActiveBookmark(myProject, Collections.singletonList(myRepository), currentBookmark));
      topShownBookmarks++;
    }
    wrapWithMoreActionIfNeeded(myProject, popupGroup, bookmarkActions, topShownBookmarks,
                               firstLevelGroup ? HgBranchPopup.SHOW_ALL_BOOKMARKS_KEY : null, firstLevelGroup);

    //only opened branches have to be shown
    popupGroup.addSeparator(specificRepository == null ?
                            HgBundle.message("hg4idea.branch.branches.separator") :
                            HgBundle.message("hg4idea.branch.branches.in.repo.separator", DvcsUtil.getShortRepositoryName(specificRepository)));
    List<BranchActions> branchActions = StreamEx.of(myRepository.getOpenedBranches())
      .sorted(StringUtil::naturalCompare)
      .filter(b -> !b.equals(myRepository.getCurrentBranch()))
      .map(b -> new BranchActions(myProject, Collections.singletonList(myRepository), b))
      .sorted(FAVORITE_BRANCH_COMPARATOR)
      .toList();
    branchActions.add(0, new CurrentBranch(myProject, Collections.singletonList(myRepository), myRepository.getCurrentBranch()));
    wrapWithMoreActionIfNeeded(myProject, popupGroup, branchActions, getNumOfTopShownBranches(branchActions) + 1,
                               firstLevelGroup ? HgBranchPopup.SHOW_ALL_BRANCHES_KEY : null, firstLevelGroup);
    return popupGroup;
  }

  public static class HgNewBranchAction extends NewBranchAction<HgRepository> {
    @NotNull final HgRepository myPreselectedRepo;

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

    public void createNewBranchInCurrentThread(@NotNull final String name) {
      for (final HgRepository repository : myRepositories) {
        try {
          HgCommandResult result = new HgBranchCreateCommand(myProject, repository.getRoot(), name).executeInCurrentThread();
          repository.update();
          if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
            new HgCommandResultNotifier(myProject)
              .notifyError(result, HgBundle.message("hg4idea.branch.creation.error"), HgBundle.message("hg4idea.branch.creation.error.msg", name));
          }
        }
        catch (HgCommandException exception) {
          HgErrorUtil.handleException(myProject, HgBundle.message("hg4idea.branch.cannot.create"), exception);
        }
      }
    }
  }

  public static class HgCloseBranchAction extends DumbAwareAction {
    @NotNull private final List<HgRepository> myRepositories;
    @NotNull final HgRepository myPreselectedRepo;

    HgCloseBranchAction(@NotNull List<HgRepository> repositories, @NotNull HgRepository preselectedRepo) {
      super(HgBundle.message("action.hg4idea.branch.close", repositories.size()),
            HgBundle.message("action.hg4idea.branch.close.description", repositories.size()),
            AllIcons.Actions.Cancel);
      myRepositories = repositories;
      myPreselectedRepo = preselectedRepo;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = myPreselectedRepo.getProject();
      StoreUtil.saveDocumentsAndProjectSettings(project);
      ChangeListManager.getInstance(project)
        .invokeAfterUpdate(() -> commitAndCloseBranch(project), InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, VcsBundle
                             .message("waiting.changelists.update.for.show.commit.dialog.message"),
                                                               ModalityState.current());
    }

    private void commitAndCloseBranch(@NotNull final Project project) {
      final LocalChangeList activeChangeList = ChangeListManager.getInstance(project).getDefaultChangeList();
      HgVcs vcs = HgVcs.getInstance(project);
      assert vcs != null;
      final HgRepositoryManager repositoryManager = HgUtil.getRepositoryManager(project);
      List<Change> changesForRepositories = ContainerUtil.filter(activeChangeList.getChanges(),
                                                                 change -> myRepositories.contains(repositoryManager.getRepositoryForFile(
                                                                   ChangesUtil.getFilePath(change))));
      HgCloseBranchExecutor closeBranchExecutor = vcs.getCloseBranchExecutor();
      closeBranchExecutor.setRepositories(myRepositories);
      CommitChangeListDialog.commitChanges(project, changesForRepositories, changesForRepositories, activeChangeList,
                                           Collections.singletonList(closeBranchExecutor), false, vcs, "Close Branch", null, false);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(ContainerUtil.and(myRepositories,
                                                                 repository -> repository.getOpenedBranches()
                                                                   .contains(repository.getCurrentBranch())));
    }
  }

  public static class HgNewBookmarkAction extends DumbAwareAction {
    @NotNull protected final List<HgRepository> myRepositories;
    @NotNull final HgRepository myPreselectedRepo;

    HgNewBookmarkAction(@NotNull List<HgRepository> repositories, @NotNull HgRepository preselectedRepo) {
      super(HgBundle.message("action.hg4idea.bookmark.new"), HgBundle.message("action.hg4idea.bookmark.new.description"), AllIcons.General.Add);
      myRepositories = repositories;
      myPreselectedRepo = preselectedRepo;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (DvcsUtil.anyRepositoryIsFresh(myRepositories)) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription(HgBundle.message("action.hg4idea.bookmark.not.possible.before.commit"));
      }
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
    @NotNull final HgRepository myRepository;
    @NotNull final String myCurrentBranchName;
    @NotNull Collection<Hash> myHeads;

    public HgShowUnnamedHeadsForCurrentBranchAction(@NotNull HgRepository repository) {
      super(Presentation.NULL_STRING, true);
      myRepository = repository;
      myCurrentBranchName = repository.getCurrentBranch();
      getTemplatePresentation().setText(HgBundle.message("action.hg4idea.show.unnamed.heads", myCurrentBranchName));
      myHeads = filterUnnamedHeads();
    }

    @NotNull
    private Collection<Hash> filterUnnamedHeads() {
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
    public void update(@NotNull final AnActionEvent e) {
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

  public static class CurrentBranch extends BranchActions implements PopupElementWithAdditionalInfo {
    public CurrentBranch(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
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

    private static class DeleteBookmarkAction extends HgBranchAbstractAction {

      DeleteBookmarkAction(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
        super(project, CommonBundle.message("button.delete"), repositories, branchName);
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

  public static class CurrentActiveBookmark extends BookmarkActions implements PopupElementWithAdditionalInfo {

    public CurrentActiveBookmark(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull String branchName) {
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
