package gitlog;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.Notificator;
import git4idea.branch.GitBranchUiHandlerImpl;
import git4idea.branch.GitBranchWorker;
import git4idea.branch.GitSmartOperationDialog;
import git4idea.cherrypick.GitCherryPicker;
import git4idea.commands.*;
import git4idea.history.browser.GitCommit;
import git4idea.rebase.GitInteractiveRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorService;
import git4idea.rebase.GitRebaser;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryFiles;
import git4idea.update.GitUpdateResult;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.data.rebase.GitActionHandler;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.parents.RebaseCommand;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.ui.UI_Controller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitActionHandlerImpl implements GitActionHandler {

  private final Project myProject;
  private final UI_Controller myUiController;
  private final GitRepository myRepository;
  private final Git myGit;
  private final GitPlatformFacade myPlatformFacade;
  private final GitLogComponent myLogComponent;

  public GitActionHandlerImpl(Project project, UI_Controller uiController) {
    myProject = project;
    myUiController = uiController;
    myLogComponent = ServiceManager.getService(project, GitLogComponent.class);
    myRepository = myLogComponent.getRepository();
    myGit = ServiceManager.getService(Git.class);
    myPlatformFacade = ServiceManager.getService(myProject, GitPlatformFacade.class);
  }

  @Override
  public void abortRebase() {
    new Task.Backgroundable(myProject, "Aborting rebase...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitRebaser rebaser = new GitRebaser(GitActionHandlerImpl.this.myProject, myGit, indicator);
        rebaser.abortRebase(myRepository.getRoot());
      }

      @Override
      public void onSuccess() {
        refresh();
      }

      @Override
      public void onCancel() {
        onSuccess();
      }
    }.queue();
  }

  @Override
  public void continueRebase() {
    new Task.Backgroundable(myProject, "Rebasing...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        new GitRebaser(GitActionHandlerImpl.this.myProject, myGit, indicator).continueRebase(myRepository.getRoot());
      }

      @Override
      public void onSuccess() {
        refresh();
      }

      @Override
      public void onCancel() {
        onSuccess();
      }
    }.queue();
  }

  @Override
  public void cherryPick(final Ref targetRef, final List<Node> nodesToPick, final GitActionHandler.Callback callback) {
    assert targetRef.getType().isLocalOrHead() : "unexpected type for cherry-pick: " + targetRef.getType();
    new Task.Backgroundable(myProject, "Cherry-picking...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        callback.disableModifications();
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
        refresh();
        callback.enableModifications();
      }

      @Override
      public void onCancel() {
        onSuccess();
      }
    }.queue();
  }

  private void checkout(Ref targetRef, ProgressIndicator indicator) {
    GitBranchWorker branchWorker =
      new GitBranchWorker(myProject, myPlatformFacade, myGit, new GitBranchUiHandlerImpl(myProject, myPlatformFacade, myGit, indicator) {
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
  public void rebase(final Node onto, final Ref subjectRef, final Callback callback) {
    doRebase(onto, subjectRef, callback, new GitRebaser(myProject, myGit, null));
  }

  private void doRebase(final Node onto, final Ref subjectRef, final Callback callback, final GitRebaser rebaser) {
    new Task.Backgroundable(myProject, "Rebasing...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        rebaser.setProgressIndicator(indicator);
        callback.disableModifications();
        List<String> params = Arrays.asList(onto.getCommitHash().toStrHash(), subjectRef.getName());
        GitUpdateResult result = rebaser.rebase(myRepository.getRoot(), params, null, new GitLineHandlerAdapter() {
          @Override
          public void onLineAvailable(String line, Key outputType) {
            // TODO report progress
          }
        });
        handleRebaseResult(result, onto, subjectRef);
      }

      @Override
      public void onSuccess() {
        refresh();
        callback.enableModifications();
      }

      @Override
      public void onCancel() {
        onSuccess();
      }
    }.queue();
  }

  private void refresh() {
    myRepository.update();
  }

  private void handleRebaseResult(GitUpdateResult result, Node onto, Ref subjectRef) {
    // TODO branch name if available
    Ref ref = myUiController.getDataPackUtils().findRefOfNode(onto);
    String target = ref == null ? onto.getCommitHash().toStrHash() : ref.getName();

    switch (result) {
      case NOTHING_TO_UPDATE:
        break;
      case SUCCESS:
        Notificator.getInstance(this.myProject).notifySuccess("", "Rebased " + subjectRef.getName() + " to " + target);
        break;
      case SUCCESS_WITH_RESOLVED_CONFLICTS:
        Notificator.getInstance(this.myProject).notifySuccess("", "Rebased " + subjectRef.getName() + " to " + target);
        break;
      case INCOMPLETE:
        break;
      case CANCEL:
        break;
      case ERROR:
        // error is notified inside the rebaser
        break;
      case NOT_READY:
        break;
    }
  }

  @Override
  public void rebaseOnto(Node onto, Ref subjectRef, List<Node> nodesToRebase, Callback callback) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void interactiveRebase(Ref subjectRef, Node onto, final Callback callback, final List<RebaseCommand> commands) {
    final GitLineHandler h = new GitLineHandler(myProject, myRepository.getRoot(), GitCommand.REBASE);
    h.addParameters("-i", "-v");

    h.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (line.toLowerCase().startsWith("rebasing")) {
          line = line.trim();
          String progress = line.substring(line.indexOf('(') + 1, line.lastIndexOf('/'));
          Integer index = null;
          try {
            index = Integer.parseInt(progress) - 1;
            if (index >= 0 && index < commands.size()) {
              callback.interactiveCommandApplied(commands.get(index));
            }
          }
          catch (NumberFormatException e) {
            e.printStackTrace();
          }
        }
      }
    });

    // for testing: h.addParameters("HEAD~" + commands.size());
    GitRebaseEditorService rebaseEditorService = GitRebaseEditorService.getInstance();

    RebaseEditor editor = new RebaseEditor(rebaseEditorService, myRepository.getRoot(), commands, h);
    Integer rebaseEditorNo = editor.getHandlerNo();
    rebaseEditorService.configureHandler(h, rebaseEditorNo);

    // TODO unregister handler

    GitRebaser rebaser = new GitRebaser(myProject, myGit, null) {
      @Override
      protected GitLineHandler createHandler(VirtualFile root) {
        return h;
      }

      @Override
      protected void makeContinueRebaseInteractiveEditor(VirtualFile root, GitLineHandler rh) {
        GitRebaseEditorService rebaseEditorService = GitRebaseEditorService.getInstance();
        RebaseEditor editor = new RebaseEditor(rebaseEditorService, root, commands, rh);
        Integer rebaseEditorNo = editor.getHandlerNo();
        rebaseEditorService.configureHandler(rh, rebaseEditorNo);
      }
    };

    doRebase(onto, subjectRef, callback, rebaser);
  }


  class RebaseEditor extends GitInteractiveRebaseEditorHandler {

    private final List<RebaseCommand> myCommands;

    public RebaseEditor(GitRebaseEditorService rebaseEditorService, VirtualFile root, List<RebaseCommand> commands, GitHandler h) {
      super(rebaseEditorService, myProject, root, h);
      myCommands = commands;
      Collections.reverse(myCommands);
    }

    public int editCommits(String path) {
      try {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), GitUtil.UTF8_ENCODING));
        try {
          if (path.toUpperCase().endsWith(GitRepositoryFiles.COMMIT_EDITMSG)) {
            String done = FileUtil.loadFile(new File(new File(new File(myRepository.getRoot().getPath(), ".git"), "rebase-merge"), "done")).trim();
            String[] tasksDone = StringUtil.splitByLines(done);
            String currentTask = tasksDone[tasksDone.length - 1].trim();
            String currentHash = currentTask.substring(currentTask.lastIndexOf(" ")).trim();
            String messageToUse = findCommitMessageForHash(currentHash);
            if (messageToUse == null) {
              System.err.println("Couldn't find the commit message for the hash to reword. " +
                                 "Looked for hash " + currentHash + " among " + myCommands);
              return 0; // using old message
            }
            w.print(messageToUse);
          }
          else {
            for (RebaseCommand command : myCommands) {
              String hash = command.getCommit().toStrHash();
              switch (command.getKind()) {
                case PICK:
                  w.print("pick " + hash + "\n");
                  break;
                case FIXUP:
                  w.print("fixup " + hash + "\n");
                  break;
                case REWORD:
                  w.print("reword " + hash + "\n");
                  break;
              }
            }
          }
        }
        finally {
          w.close();
        }
        return 0;
      }
      catch (Exception ex) {
        System.err.println("Editor failed: " + ex);
        ex.printStackTrace();
        return 1;
      }
    }

    @Nullable
    private String findCommitMessageForHash(String hash) {
      for (RebaseCommand command : myCommands) {
        if (command.getCommit().toStrHash().startsWith(hash)) {
          return command.getNewMessage();
        }
      }
      return null;
    }
  }

}
