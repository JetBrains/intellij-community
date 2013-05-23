package gitlog;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.branch.GitBranchUiHandlerImpl;
import git4idea.branch.GitBranchWorker;
import git4idea.branch.GitSmartOperationDialog;
import git4idea.cherrypick.GitCherryPicker;
import git4idea.commands.Git;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.data.rebase.GitActionHandler;
import org.hanuna.gitalk.data.rebase.RebaseCommand;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.refs.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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
  public void cherryPick(final Ref targetRef, final List<Node> nodesToPick, final GitActionHandler.Callback callback) {
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
        List<GitCommit> commits = ContainerUtil.map(nodesToPick, new Function<Node, GitCommit>() {
          @Override
          public GitCommit fun(Node node) {
            return commitDataGetter.getCommitData(node).getFullCommit();
          }
        });
        cherryPicker.cherryPick(Collections.singletonMap(myRepository, commits));
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

}
