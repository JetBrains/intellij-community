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
package git4idea.checkin;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersion;
import git4idea.config.GitVersionSpecialty;
import git4idea.crlf.GitCrlfDialog;
import git4idea.crlf.GitCrlfProblemsDetector;
import git4idea.crlf.GitCrlfUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Prohibits committing with an empty messages, warns if committing into detached HEAD, checks if user name and correct CRLF attributes
 * are set.
 * @author Kirill Likhodedov
*/
public class GitCheckinHandlerFactory extends VcsCheckinHandlerFactory {

  private static final Logger LOG = Logger.getInstance(GitCheckinHandlerFactory.class);

  public GitCheckinHandlerFactory() {
    super(GitVcs.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(final CheckinProjectPanel panel) {
    return new MyCheckinHandler(panel);
  }

  private class MyCheckinHandler extends CheckinHandler {
    @NotNull private final CheckinProjectPanel myPanel;
    @NotNull private final Project myProject;


    public MyCheckinHandler(@NotNull CheckinProjectPanel panel) {
      myPanel = panel;
      myProject = myPanel.getProject();
    }

    @Override
    public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
      if (emptyCommitMessage()) {
        return ReturnResult.CANCEL;
      }

      if (commitOrCommitAndPush(executor)) {
        ReturnResult result = checkUserName();
        if (result != ReturnResult.COMMIT) {
          return result;
        }
        result = warnAboutCrlfIfNeeded();
        if (result != ReturnResult.COMMIT) {
          return result;
        }
        return warnAboutDetachedHeadIfNeeded();
      }
      return ReturnResult.COMMIT;
    }

    @NotNull
    private ReturnResult warnAboutCrlfIfNeeded() {
      GitVcsSettings settings = GitVcsSettings.getInstance(myProject);
      if (!settings.warnAboutCrlf()) {
        return ReturnResult.COMMIT;
      }

      final GitPlatformFacade platformFacade = ServiceManager.getService(myProject, GitPlatformFacade.class);
      final Git git = ServiceManager.getService(Git.class);

      final Collection<VirtualFile> files = myPanel.getVirtualFiles(); // deleted files aren't included, but for them we don't care about CRLFs.
      final AtomicReference<GitCrlfProblemsDetector> crlfHelper = new AtomicReference<GitCrlfProblemsDetector>();
      ProgressManager.getInstance().run(
        new Task.Modal(myProject, "Checking for line separator issues...", true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            crlfHelper.set(GitCrlfProblemsDetector.detect(GitCheckinHandlerFactory.MyCheckinHandler.this.myProject,
                                                          platformFacade, git, files));
          }
        });

      if (crlfHelper.get() == null) { // detection cancelled
        return ReturnResult.CANCEL;
      }

      if (crlfHelper.get().shouldWarn()) {
        final GitCrlfDialog dialog = new GitCrlfDialog(myProject);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            dialog.show();
          }
        });
        int decision = dialog.getExitCode();
        if  (decision == GitCrlfDialog.CANCEL) {
          return ReturnResult.CANCEL;
        }
        else {
          if (decision == GitCrlfDialog.SET) {
            VirtualFile anyRoot = myPanel.getRoots().iterator().next(); // config will be set globally => any root will do.
            setCoreAutoCrlfAttribute(anyRoot);
          }
          else {
            if (dialog.dontWarnAgain()) {
              settings.setWarnAboutCrlf(false);
            }
          }
          return ReturnResult.COMMIT;
        }
      }
      return ReturnResult.COMMIT;
    }

    private void setCoreAutoCrlfAttribute(@NotNull VirtualFile aRoot) {
      try {
        GitConfigUtil.setValue(myProject, aRoot, GitConfigUtil.CORE_AUTOCRLF, GitCrlfUtil.RECOMMENDED_VALUE, "--global");
      }
      catch (VcsException e) {
        // it is not critical: the user just will get the dialog again next time
        LOG.warn("Couldn't globally set core.autocrlf in " + aRoot, e);
      }
    }

    private ReturnResult checkUserName() {
      Project project = myPanel.getProject();
      GitVcs vcs = GitVcs.getInstance(project);
      assert vcs != null;

      Collection<VirtualFile> notDefined = new ArrayList<VirtualFile>();
      Map<VirtualFile, Pair<String, String>> defined = new HashMap<VirtualFile, Pair<String, String>>();
      Collection<VirtualFile> allRoots = new ArrayList<VirtualFile>(Arrays.asList(
        ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)));

      Collection<VirtualFile> affectedRoots = getSelectedRoots();
      for (VirtualFile root : affectedRoots) {
        try {
          Pair<String, String> nameAndEmail = getUserNameAndEmailFromGitConfig(project, root);
          String name = nameAndEmail.getFirst();
          String email = nameAndEmail.getSecond();
          if (name == null || email == null) {
            notDefined.add(root);
          }
          else {
            defined.put(root, nameAndEmail);
          }
        }
        catch (VcsException e) {
          LOG.error("Couldn't get user.name and user.email for root " + root, e);
          // doing nothing - let commit with possibly empty user.name/email
        }
      }

      if (notDefined.isEmpty()) {
        return ReturnResult.COMMIT;
      }

      GitVersion version = vcs.getVersion();
      if (System.getenv("HOME") == null && GitVersionSpecialty.DOESNT_DEFINE_HOME_ENV_VAR.existsIn(version)) {
        Messages.showErrorDialog(project,
                                 "You are using Git " + version + " which doesn't define %HOME% environment variable properly.\n" +
                                 "Consider updating Git to a newer version " +
                                 "or define %HOME% to point to the place where the global .gitconfig is stored \n" +
                                 "(it is usually %USERPROFILE% or %HOMEDRIVE%%HOMEPATH%).",
                                 "HOME Variable Is Not Defined");
        return ReturnResult.CANCEL;
      }

      if (defined.isEmpty() && allRoots.size() > affectedRoots.size()) {
        allRoots.removeAll(affectedRoots);
        for (VirtualFile root : allRoots) {
          try {
            Pair<String, String> nameAndEmail = getUserNameAndEmailFromGitConfig(project, root);
            String name = nameAndEmail.getFirst();
            String email = nameAndEmail.getSecond();
            if (name != null && email != null) {
              defined.put(root, nameAndEmail);
              break;
            }
          }
          catch (VcsException e) {
            LOG.error("Couldn't get user.name and user.email for root " + root, e);
            // doing nothing - not critical not to find the values for other roots not affected by commit
          }
        }
      }

      GitUserNameNotDefinedDialog dialog = new GitUserNameNotDefinedDialog(project, notDefined, affectedRoots, defined);
      dialog.show();
      if (dialog.isOK()) {
        try {
          if (dialog.isGlobal()) {
            GitConfigUtil.setValue(project, notDefined.iterator().next(), GitConfigUtil.USER_NAME, dialog.getUserName(), "--global");
            GitConfigUtil.setValue(project, notDefined.iterator().next(), GitConfigUtil.USER_EMAIL, dialog.getUserEmail(), "--global");
          }
          else {
            for (VirtualFile root : notDefined) {
              GitConfigUtil.setValue(project, root, GitConfigUtil.USER_NAME, dialog.getUserName());
              GitConfigUtil.setValue(project, root, GitConfigUtil.USER_EMAIL, dialog.getUserEmail());
            }
          }
        }
        catch (VcsException e) {
          String message = "Couldn't set user.name and user.email";
          LOG.error(message, e);
          Messages.showErrorDialog(myPanel.getComponent(), message);
          return ReturnResult.CANCEL;
        }
        return ReturnResult.COMMIT;
      }
      return ReturnResult.CLOSE_WINDOW;
    }

    @NotNull
    private Pair<String, String> getUserNameAndEmailFromGitConfig(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
      String name = GitConfigUtil.getValue(project, root, GitConfigUtil.USER_NAME);
      String email = GitConfigUtil.getValue(project, root, GitConfigUtil.USER_EMAIL);
      return Pair.create(name, email);
    }

    private boolean emptyCommitMessage() {
      if (myPanel.getCommitMessage().trim().isEmpty()) {
        Messages.showMessageDialog(myPanel.getComponent(), GitBundle.message("git.commit.message.empty"),
                                   GitBundle.message("git.commit.message.empty.title"), Messages.getErrorIcon());
        return true;
      }
      return false;
    }

    private ReturnResult warnAboutDetachedHeadIfNeeded() {
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

      final int choice = Messages.showOkCancelDialog(myPanel.getComponent(), XmlStringUtil.wrapInHtml(message), title,
                                                                                                      "Cancel", "Commit",
                                                                                                      Messages.getWarningIcon());
      if (choice != Messages.OK) {
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
      GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(myPanel.getProject());
      for (VirtualFile root : getSelectedRoots()) {
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

    @NotNull
    private Collection<VirtualFile> getSelectedRoots() {
      ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
      Collection<VirtualFile> result = new HashSet<VirtualFile>();
      for (FilePath path : ChangesUtil.getPaths(myPanel.getSelectedChanges())) {
        VirtualFile root = vcsManager.getVcsRootFor(path);
        if (root != null) {
          result.add(root);
        }
      }
      return result;
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
