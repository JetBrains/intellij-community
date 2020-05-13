// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.config.*;
import git4idea.crlf.GitCrlfDialog;
import git4idea.crlf.GitCrlfProblemsDetector;
import git4idea.crlf.GitCrlfUtil;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Prohibits committing with an empty messages, warns if committing into detached HEAD, checks if user name and correct CRLF attributes
 * are set.
 */
public class GitCheckinHandlerFactory extends VcsCheckinHandlerFactory {

  private static final Logger LOG = Logger.getInstance(GitCheckinHandlerFactory.class);

  public GitCheckinHandlerFactory() {
    super(GitVcs.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    return new MyCheckinHandler(panel);
  }

  private static class MyCheckinHandler extends CheckinHandler {
    @NotNull private final CheckinProjectPanel myPanel;
    @NotNull private final Project myProject;


    MyCheckinHandler(@NotNull CheckinProjectPanel panel) {
      myPanel = panel;
      myProject = myPanel.getProject();
    }

    @Override
    public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
      if (emptyCommitMessage()) {
        return ReturnResult.CANCEL;
      }

      if (commitOrCommitAndPush(executor)) {
        ReturnResult result = checkGitVersionAndEnv();
        if (result != ReturnResult.COMMIT) {
          return result;
        }
        result = checkUserName();
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

      final Git git = Git.getInstance();

      final Collection<VirtualFile> files = myPanel.getVirtualFiles(); // deleted files aren't included, but for them we don't care about CRLFs.
      final AtomicReference<GitCrlfProblemsDetector> crlfHelper = new AtomicReference<>();
      ProgressManager.getInstance().run(
        new Task.Modal(myProject, "Checking for Line Separator Issues...", true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            crlfHelper.set(GitCrlfProblemsDetector.detect(GitCheckinHandlerFactory.MyCheckinHandler.this.myProject,
                                                          git, files));
          }
        });

      if (crlfHelper.get() == null) { // detection cancelled
        return ReturnResult.CANCEL;
      }

      if (crlfHelper.get().shouldWarn()) {
        Pair<Integer, Boolean> codeAndDontWarn = UIUtil.invokeAndWaitIfNeeded(() -> {
          final GitCrlfDialog dialog = new GitCrlfDialog(myProject);
          dialog.show();
          return Pair.create(dialog.getExitCode(), dialog.dontWarnAgain());
        });
        int decision = codeAndDontWarn.first;
        boolean dontWarnAgain = codeAndDontWarn.second;

        if  (decision == GitCrlfDialog.CANCEL) {
          return ReturnResult.CANCEL;
        }
        else {
          if (decision == GitCrlfDialog.SET) {
            VirtualFile anyRoot = myPanel.getRoots().iterator().next(); // config will be set globally => any root will do.
            setCoreAutoCrlfAttribute(anyRoot);
          }
          else {
            if (dontWarnAgain) {
              settings.setWarnAboutCrlf(false);
            }
          }
          return ReturnResult.COMMIT;
        }
      }
      return ReturnResult.COMMIT;
    }

    private void setCoreAutoCrlfAttribute(@NotNull VirtualFile aRoot) {
      ProgressManager.getInstance().run(new Task.Modal(myProject, "Updating Git Config...", true) {
        @Override
        public void run(@NotNull ProgressIndicator pi) {
          try {
            GitConfigUtil.setValue(myProject, aRoot, GitConfigUtil.CORE_AUTOCRLF, GitCrlfUtil.RECOMMENDED_VALUE, "--global");
          }
          catch (VcsException e) {
            // it is not critical: the user just will get the dialog again next time
            LOG.warn("Couldn't globally set core.autocrlf in " + aRoot, e);
          }
        }
      });
    }

    private ReturnResult checkGitVersionAndEnv() {
      GitVersion version = GitExecutableManager.getInstance().getVersionUnderModalProgressOrCancel(myProject);
      if (System.getenv("HOME") == null && GitVersionSpecialty.DOESNT_DEFINE_HOME_ENV_VAR.existsIn(version)) {
        Messages.showErrorDialog(myProject,
                                 "You are using Git " +
                                 version.getPresentation() +
                                 " which doesn't define %HOME% environment variable properly.\n" +
                                 "Consider updating Git to a newer version " +
                                 "or define %HOME% to point to the place where the global .gitconfig is stored \n" +
                                 "(it is usually %USERPROFILE% or %HOMEDRIVE%%HOMEPATH%).",
                                 "HOME Variable Is Not Defined");
        return ReturnResult.CANCEL;
      }
      return ReturnResult.COMMIT;
    }

    private ReturnResult checkUserName() {
      final Project project = myPanel.getProject();
      GitVcs vcs = GitVcs.getInstance(project);

      Collection<VirtualFile> affectedRoots = getSelectedRoots();
      Map<VirtualFile, Couple<String>> defined = getDefinedUserNames(project, affectedRoots, false);

      Collection<VirtualFile> allRoots = new ArrayList<>(Arrays.asList(ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)));
      Collection<VirtualFile> notDefined = new ArrayList<>(affectedRoots);
      notDefined.removeAll(defined.keySet());

      if (notDefined.isEmpty()) {
        return ReturnResult.COMMIT;
      }

      // try to find a root with defined user name among other roots - to propose this user name in the dialog
      if (defined.isEmpty() && allRoots.size() > affectedRoots.size()) {
        allRoots.removeAll(affectedRoots);
        defined.putAll(getDefinedUserNames(project, allRoots, true));
      }

      final GitUserNameNotDefinedDialog dialog = new GitUserNameNotDefinedDialog(project, notDefined, affectedRoots, defined);
      if (dialog.showAndGet()) {
        GitVcsSettings.getInstance(project).setUserNameGlobally(dialog.isGlobal());
        return setUserNameUnderProgress(project, notDefined, dialog) ? ReturnResult.COMMIT : ReturnResult.CANCEL;
      }
      return ReturnResult.CLOSE_WINDOW;
    }

    @NotNull
    private static Map<VirtualFile, Couple<String>> getDefinedUserNames(@NotNull final Project project,
                                                                        @NotNull final Collection<? extends VirtualFile> roots,
                                                                        final boolean stopWhenFoundFirst) {
      final Map<VirtualFile, Couple<String>> defined = new HashMap<>();
      ProgressManager.getInstance().run(new Task.Modal(project, "Checking Git User Name...", true) {
        @Override
        public void run(@NotNull ProgressIndicator pi) {
          for (VirtualFile root : roots) {
            try {
              Couple<String> nameAndEmail = getUserNameAndEmailFromGitConfig(project, root);
              String name = nameAndEmail.getFirst();
              String email = nameAndEmail.getSecond();
              if (name != null && email != null) {
                defined.put(root, nameAndEmail);
                if (stopWhenFoundFirst) {
                  return;
                }
              }
            }
            catch (VcsException e) {
              LOG.error("Couldn't get user.name and user.email for root " + root, e);
              // doing nothing - let commit with possibly empty user.name/email
            }
          }
        }
      });
      return defined;
    }

    private boolean setUserNameUnderProgress(@NotNull final Project project,
                                             @NotNull final Collection<? extends VirtualFile> notDefined,
                                             @NotNull final GitUserNameNotDefinedDialog dialog) {
      final Ref<String> error = Ref.create();
      ProgressManager.getInstance().run(new Task.Modal(project, "Setting Git User Name...", true) {
        @Override
        public void run(@NotNull ProgressIndicator pi) {
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
            error.set(message);
          }
        }
      });
      if (error.isNull()) {
        return true;
      }
      else {
        Messages.showErrorDialog(myPanel.getComponent(), error.get());
        return false;
      }
    }

    @NotNull
    private static Couple<String> getUserNameAndEmailFromGitConfig(@NotNull Project project,
                                                                   @NotNull VirtualFile root) throws VcsException {
      String name = GitConfigUtil.getValue(project, root, GitConfigUtil.USER_NAME);
      String email = GitConfigUtil.getValue(project, root, GitConfigUtil.USER_EMAIL);
      return Couple.of(name, email);
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
      if (detachedRoot == null || !GitVcsSettings.getInstance(myProject).warnAboutDetachedHead()) {
        return ReturnResult.COMMIT;
      }

      final String title;
      final String message;
      final CharSequence rootPath = detachedRoot.myRoot.getPresentableUrl();
      final String messageCommonStart = "The Git repository at the following path";
      if (detachedRoot.myRebase) {
        title = "Unfinished Rebase Process";
        message = messageCommonStart + " has an <b>unfinished rebase</b> process: <br/>" +
                  "<b>" + rootPath + "</b><br>" +
                  "You probably want to <b>continue rebase</b> instead of committing. <br/>" +
                  "Committing during rebase may lead to the commit loss. <br/>" +
                  readMore("https://www.kernel.org/pub/software/scm/git/docs/git-rebase.html", "Read more about Git rebase");
      } else {
        title = "Commit in Detached HEAD";
        message = messageCommonStart + " is in the <b>detached HEAD</b> state: <br/>" +
                  "<b>" + rootPath + "</b><br>" +
                  "You can look around, make experimental changes and commit them, but be sure to checkout a branch not to lose your work. <br/>" +
                  "Otherwise you risk losing your changes. <br/>" +
                  readMore("http://gitolite.com/detached-head.html", "Read more about detached HEAD");
      }

      DialogWrapper.DoNotAskOption dontAskAgain = new DialogWrapper.DoNotAskOption.Adapter() {
        @Override
        public void rememberChoice(boolean isSelected, int exitCode) {
          GitVcsSettings.getInstance(myProject).setWarnAboutDetachedHead(!isSelected);
        }

        @NotNull
        @Override
        public String getDoNotShowMessage() {
          return "Don't warn again";
        }
      };
      int choice = Messages.showOkCancelDialog(myProject, XmlStringUtil.wrapInHtml(message), title, "Commit",
                                               CommonBundle.getCancelButtonText(), Messages.getWarningIcon(), dontAskAgain);
      if (choice == Messages.OK) {
        return ReturnResult.COMMIT;
      } else {
        return ReturnResult.CLOSE_WINDOW;
      }
    }

    private static boolean commitOrCommitAndPush(@Nullable CommitExecutor executor) {
      return executor == null || executor instanceof GitCommitAndPushExecutor;
    }

    @NotNull
    private static String readMore(@NotNull String link, @NotNull String message) {
      return String.format("<a href='%s'>%s</a>.", link, message);
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
        GitRepository repository = repositoryManager.getRepositoryForRootQuick(root);
        if (repository == null) {
          continue;
        }
        if (!repository.isOnBranch() && !GitRebaseUtils.isInteractiveRebaseInProgress(repository)) {
          return new DetachedRoot(root, repository.isRebaseInProgress());
        }
      }
      return null;
    }

    @NotNull
    private Collection<VirtualFile> getSelectedRoots() {
      ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
      GitVcs git = GitVcs.getInstance(myProject);
      Collection<VirtualFile> result = new HashSet<>();
      for (FilePath path : ChangesUtil.getPaths(myPanel.getSelectedChanges())) {
        VcsRoot vcsRoot = vcsManager.getVcsRootObjectFor(path);
        if (vcsRoot != null) {
          VirtualFile root = vcsRoot.getPath();
          if (git.equals(vcsRoot.getVcs())) {
            result.add(root);
          }
        }
      }
      return result;
    }

    private static class DetachedRoot {
      final VirtualFile myRoot;
      final boolean myRebase; // rebase in progress, or just detached due to a checkout of a commit.

      DetachedRoot(@NotNull VirtualFile root, boolean rebase) {
        myRoot = root;
        myRebase = rebase;
      }
    }
  }
}
