/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.NewBranchAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgBookmarkCommand;
import org.zmlx.hg4idea.command.HgBranchCreateCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgBookmarkDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.*;

import static org.zmlx.hg4idea.util.HgUtil.*;

public class HgBranchPopupActions {

  private final Project myProject;
  private final HgRepository myRepository;

  HgBranchPopupActions(Project project, HgRepository repository) {
    myProject = project;
    myRepository = repository;
  }

  ActionGroup createActions(@Nullable DefaultActionGroup toInsert) {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addAction(new HgNewBranchAction(myProject, Collections.singletonList(myRepository), myRepository.getRoot()));
    popupGroup.addAction(new HgNewBookmarkAction(myProject, Collections.singletonList(myRepository), myRepository.getRoot()));
    popupGroup.addAction(new HgShowUnnamedHeadsForCurrentBranchAction(myProject, myRepository));
    if (toInsert != null) {
      popupGroup.addAll(toInsert);
    }

    popupGroup.addSeparator("Bookmarks");
    List<String> bookmarkNames = getNamesWithoutHashes(myRepository.getBookmarks());
    String currentBookmark = myRepository.getCurrentBookmark();
    for (String bookmark : bookmarkNames) {
      AnAction bookmarkAction = new BookmarkActions(myProject, myRepository, bookmark);
      if (bookmark.equals(currentBookmark)) {
        bookmarkAction.getTemplatePresentation().setIcon(PlatformIcons.CHECK_ICON);
      }
      popupGroup.add(bookmarkAction);
    }

    popupGroup.addSeparator("Branches");
    List<String> branchNamesList = new ArrayList<String>(myRepository.getOpenedBranches());//only opened branches have to be shown
    Collections.sort(branchNamesList);
    for (String branch : branchNamesList) {
      if (!branch.equals(myRepository.getCurrentBranch())) { // don't show current branch in the list
        popupGroup.add(new HgCommonBranchActions(myProject, myRepository, branch));
      }
    }
    return popupGroup;
  }

  private static class HgNewBranchAction extends NewBranchAction<HgRepository> {
    @NotNull final VirtualFile myPreselectedRepo;

    HgNewBranchAction(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull VirtualFile preselectedRepo) {
      super(project, repositories);
      myPreselectedRepo = preselectedRepo;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final String name = getNewBranchNameFromUser(myProject, "Create New Branch");
      if (name == null) {
        return;
      }
      try {
        new HgBranchCreateCommand(myProject, myPreselectedRepo, name).execute(new HgCommandResultHandler() {
          @Override
          public void process(@Nullable HgCommandResult result) {
            getRepositoryManager(myProject).updateRepository(myPreselectedRepo);
            if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
              new HgCommandResultNotifier(myProject)
                .notifyError(result, "Creation failed", "Branch creation [" + name + "] failed");
            }
          }
        });
      }
      catch (HgCommandException exception) {
        HgAbstractGlobalAction.handleException(myProject, "Can't create new branch: ", exception);
      }
    }
  }

  private static class HgNewBookmarkAction extends DumbAwareAction {
    protected final List<HgRepository> myRepositories;
    protected Project myProject;
    @NotNull final VirtualFile myPreselectedRepo;

    HgNewBookmarkAction(@NotNull Project project, @NotNull List<HgRepository> repositories, @NotNull VirtualFile preselectedRepo) {
      super("New Bookmark", "Create new bookmark", null);
      myProject = project;
      myRepositories = repositories;
      myPreselectedRepo = preselectedRepo;
    }

    @Override
    public void update(AnActionEvent e) {
      if (DvcsUtil.anyRepositoryIsFresh(myRepositories)) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Bookmark creation is not possible before the first commit.");
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {

      final HgBookmarkDialog bookmarkDialog = new HgBookmarkDialog(myProject);
      bookmarkDialog.show();
      if (bookmarkDialog.isOK()) {
        try {
          final String name = bookmarkDialog.getName();
          new HgBookmarkCommand(myProject, myPreselectedRepo, name).createBookmark(bookmarkDialog.isActive());
        }
        catch (HgCommandException exception) {
          HgAbstractGlobalAction.handleException(myProject, exception);
        }
      }
    }
  }

  static private class HgShowUnnamedHeadsForCurrentBranchAction extends ActionGroup {
    @NotNull final Project myProject;
    @NotNull final HgRepository myRepository;
    @NotNull final String myCurrentBranchName;
    @NotNull Collection<Hash> myHeads = new HashSet<Hash>();

    public HgShowUnnamedHeadsForCurrentBranchAction(@NotNull Project project,
                                                    @NotNull HgRepository repository) {
      super(null, true);
      myProject = project;
      myRepository = repository;
      myCurrentBranchName = repository.getCurrentBranch();
      getTemplatePresentation().setText(String.format("Unnamed heads for %s", myCurrentBranchName));
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          myHeads = filterUnnamedHeads();
        }
      });
    }

    @NotNull
    private Collection<Hash> filterUnnamedHeads() {
      Collection<Hash> branchWithHashes = myRepository.getBranches().get(myCurrentBranchName);
      if (branchWithHashes == null) {
        // repository is fresh or branch is fresh.
        return Collections.emptySet();
      }
      else {
        List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(myRepository.getRoot());
        if (parents.size() == 1) {
          Collection<Hash> bookmarkHashes = ContainerUtil.map(myRepository.getBookmarks(), new Function<HgNameWithHashInfo, Hash>() {

            @Override
            public Hash fun(HgNameWithHashInfo info) {
              return info.getHash();
            }
          });
          branchWithHashes.removeAll(bookmarkHashes);
          branchWithHashes.remove(HashImpl.build(parents.get(0).getChangeset()));
        }
      }
      return branchWithHashes;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      List<AnAction> branchHeadActions = new ArrayList<AnAction>();
      for (Hash hash : myHeads) {
        branchHeadActions.add(new HgCommonBranchActions(myProject, myRepository, hash.toShortString()));
      }
      return ContainerUtil.toArray(branchHeadActions, new AnAction[branchHeadActions.size()]);
    }

    @Override
    public void update(final AnActionEvent e) {
      if (myRepository.isFresh()) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Checkout of a new branch is not possible before the first commit.");
      }
      else if (!Repository.State.NORMAL.equals(myRepository.getState())) {
        e.getPresentation().setEnabled(false);
      }
    }
  }

  /**
   * Actions available for  bookmarks.
   */
  static class BookmarkActions extends HgCommonBranchActions {

    BookmarkActions(@NotNull Project project, @NotNull HgRepository selectedRepository, @NotNull String branchName) {
      super(project, selectedRepository, branchName);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return ArrayUtil.append(super.getChildren(e), new DeleteBookmarkAction(myProject, mySelectedRepository, myBranchName));
    }

    private static class DeleteBookmarkAction extends HgBranchAbstractAction {

      DeleteBookmarkAction(@NotNull Project project, @NotNull HgRepository selectedRepository, @NotNull String branchName) {
        super(project, "Delete", selectedRepository, branchName);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        try {
          new HgBookmarkCommand(myProject, mySelectedRepository.getRoot(), myBranchName).deleteBookmark();
        }
        catch (HgCommandException exception) {
          HgAbstractGlobalAction.handleException(myProject, exception);
        }
      }
    }
  }
}
