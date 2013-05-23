package gitlog;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.branch.GitBranchUiHandlerImpl;
import git4idea.branch.GitBranchWorker;
import git4idea.branch.GitSmartOperationDialog;
import git4idea.cherrypick.GitCherryPicker;
import git4idea.commands.Git;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SHAHash;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.repo.GitRepository;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.ui.GitActionHandler;
import org.hanuna.gitalk.ui.RebaseCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitActionHandlerImpl implements GitActionHandler {

  private final Project myProject;
  private final GitRepository myRepository;
  private final Git myGit;
  private final GitPlatformFacade myPlatformFacade;
  private final GitLogComponent myLogComponent;

  public GitActionHandlerImpl(Project project) {
    myProject = project;
    myLogComponent = ServiceManager.getService(project, GitLogComponent.class);
    myRepository = myLogComponent.getRepository();
    myGit = ServiceManager.getService(Git.class);
    myPlatformFacade = ServiceManager.getService(myProject, GitPlatformFacade.class);
  }

  @Override
  public void cherryPick(final Ref targetRef, final List<Node> nodesToPick, final Callback callback) {
    assert targetRef.getType() == Ref.RefType.LOCAL_BRANCH;
    new Task.Backgroundable(myProject, "Cherry-picking", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitLocalBranch currentBranch = myRepository.getCurrentBranch();
        if (currentBranch == null || !currentBranch.getName().equals(targetRef.getName())) {
          indicator.setText("Checking out " + targetRef.getName());
          checkout(targetRef, indicator);
        }

        GitCherryPicker cherryPicker = new GitCherryPicker(GitActionHandlerImpl.this.myProject, myGit, myPlatformFacade, true);

        final CommitDataGetter commitDataGetter = myLogComponent.getUiController().getDataPack().getCommitDataGetter();
        cherryPicker.cherryPick(Collections.singletonMap(myRepository, Lists.transform(nodesToPick, new Function<Node, GitCommit>() {
          @Override
          public GitCommit apply(@Nullable Node input) {
            return convertNodeToCommit(input, commitDataGetter);
          }
        })));
      }

      @Override
      public void onSuccess() {
        callback.enableModifications();
      }

      @Override
      public void onCancel() {
        onSuccess();
      }
    }.queue();
  }

  private void checkout(Ref targetRef, ProgressIndicator indicator) {
    GitBranchWorker branchWorker = new GitBranchWorker(myProject, myPlatformFacade, myGit,
                                                       new GitBranchUiHandlerImpl(myProject, myPlatformFacade, myGit, indicator) {
        @Override
        public int showSmartOperationDialog(@NotNull Project project,
                                            @NotNull List<Change> changes,
                                            @NotNull String operation,
                                            boolean force) {
          return GitSmartOperationDialog.SMART_EXIT_CODE;
        }
      });
    branchWorker.checkout(targetRef.getName(), Collections.singletonList(myRepository));
  }

  @Override
  public void rebase(Node onto, Ref subjectRef, Callback callback) {
  }

  @Override
  public void rebaseOnto(Node onto, Ref subjectRef, List<Node> nodesToRebase, Callback callback) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void interactiveRebase(Ref subjectRef, List<RebaseCommand> commands, Callback callback) {
    throw new UnsupportedOperationException();
  }

  private GitCommit convertNodeToCommit(Node node, CommitDataGetter commitDataGetter) {
    CommitData commit = commitDataGetter.getCommitData(node);
    AbstractHash abstractHash = AbstractHash.create(commit.getCommitHash().toStrHash());
    // TODO author == committer
    return new GitCommit(myRepository.getRoot(), abstractHash, SHAHash.emulate(abstractHash), commit.getAuthor(),
                         commit.getAuthor(), new Date(commit.getTimeStamp()), subject(commit.getMessage()),
                         description(commit.getMessage()), null, null, null, null, null, null, null, null, commit.getTimeStamp());
  }

  private String description(String commitMessage) {
    return commitMessage.substring(commitMessage.indexOf("\n"));
  }

  private String subject(String commitMessage) {
    return commitMessage.substring(0, commitMessage.indexOf("\n"));
  }

}
