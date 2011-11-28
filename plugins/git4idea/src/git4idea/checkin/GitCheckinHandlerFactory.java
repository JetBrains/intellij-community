/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.checkin;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Prohibits commiting with an empty messages.
 * @author Kirill Likhodedov
*/
public class GitCheckinHandlerFactory extends VcsCheckinHandlerFactory {
  public GitCheckinHandlerFactory() {
    super(GitVcs.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(final CheckinProjectPanel panel) {
    return new MyCheckinHandler(panel);
  }

  private class MyCheckinHandler extends CheckinHandler {
    private CheckinProjectPanel myPanel;

    public MyCheckinHandler(CheckinProjectPanel panel) {
      myPanel = panel;
    }

    @Override
    public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
      // empty commit message check
      if (myPanel.getCommitMessage().trim().isEmpty()) {
        Messages.showMessageDialog(myPanel.getComponent(), GitBundle.message("git.commit.message.empty"),
                                   GitBundle.message("git.commit.message.empty.title"), Messages.getErrorIcon());
        return ReturnResult.CANCEL;
      }
      
      if (!commitOrCommitAndPush(executor)) {
        return ReturnResult.COMMIT;
      }

      // Warning: commit on a detached HEAD
      DetachedRoot detachedRoot = getDetachedRoot();
      if (detachedRoot == null) {
        return ReturnResult.COMMIT;
      }

      final String title;
      final String message;
      final CharSequence rootPath = StringUtil.last(detachedRoot.myRoot.getPresentableUrl(), 50, true);
      final String messageCommonStart = "The Git repository <code>" + rootPath + "</code>";
      if (detachedRoot.myRebase) {
        title = "Unfinished rebase process";
        message = messageCommonStart + " <br/> has an <b>unfinished rebase</b> process. <br/>" +
                  "You probably want to <b>continue rebase</b> instead of committing. <br/>" +
                  "Committing during rebase may lead to the commit loss. <br/>" +
                  readMore("http://www.kernel.org/pub/software/scm/git/docs/git-rebase.html", "Read more about Git rebase");
      } else {
        title = "Commit in detached HEAD may be dangerous";
        message = messageCommonStart + " is in the <b>detached HEAD</b> state. <br/>" +
                  "You can look around, make experimental changes and commit them, but be sure to checkout a branch not to lose your work. <br/>" +
                  "Otherwise you risk losing your changes. <br/>" +
                  readMore("http://sitaramc.github.com/concepts/detached-head.html", "Read more about detached HEAD");
      }

      final int choice = Messages.showOkCancelDialog(myPanel.getComponent(), "<html>" + message + "</html>", title,
                                             "Cancel", "Commit", Messages.getWarningIcon());
      if (choice == 1) {
        return ReturnResult.COMMIT;
      } else {
        return ReturnResult.CLOSE_WINDOW;
      }
    }

    private boolean commitOrCommitAndPush(@Nullable CommitExecutor executor) {
      return executor == null || executor instanceof GitCommitAndPushExecutor;
    }

    private String readMore(String link, String message) {
      if (Messages.canShowMacSheetPanel()) {
        return message + ":\n" + link;
      }
      else {
        return String.format("<a href='%s'>%s</a>.", link, message);
      }
    }

    /**
     * Scans the Git roots, selected for commit, for the root which is on a detached HEAD.
     * Returns null, if all repositories are on the branch.
     * There might be several detached repositories, - in that case only one is returned.
     * This is because the situation is very rare, while it requires a lot of additional effort of making a well-formed message.
     */
    @Nullable
    private DetachedRoot getDetachedRoot() {
      GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(myPanel.getProject());
      for (VirtualFile root : myPanel.getRoots()) {
        GitRepository repository = repositoryManager.getRepositoryForRoot(root);
        if (repository == null) {
          continue;
        }
        if (!repository.isOnBranch()) {
          return new DetachedRoot(root, repository.isRebaseInProgress());
        }
      }
      return null;
    }

    private class DetachedRoot {
      final VirtualFile myRoot;
      final boolean myRebase; // rebase in progress, or just detached due to a checkout of a commit.

      public DetachedRoot(@NotNull VirtualFile root, boolean rebase) {
        myRoot = root;
        myRebase = rebase;
      }
    }

  }

}
