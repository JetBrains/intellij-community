package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitHandler;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseDialog;
import git4idea.rebase.GitRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorMain;
import git4idea.rebase.GitRebaseEditorService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Git rebase action
 */
public class GitRebase extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("rebase.action.name");
  }

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    GitRebaseDialog dialog = new GitRebaseDialog(project, gitRoots, defaultRoot);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    GitLineHandler h = dialog.handler();
    GitRebaseEditorService service = GitRebaseEditorService.getInstance();
    GitRebaseEditorHandler editor = service.getHandler(project, dialog.gitRoot());
    affectedRoots.add(dialog.gitRoot());
    try {
      h.setenv(GitHandler.GIT_EDITOR_ENV, service.getEditorCommand());
      h.setenv(GitRebaseEditorMain.IDEA_REBASE_HANDER_NO, Integer.toString(editor.getHandlerNo()));
      GitHandlerUtil.doSynchronously(h, GitBundle.getString("rebasing.title"), h.printableCommandLine());
    }
    finally {
      editor.close();
    }

    //To change body of implemented methods use File | Settings | File Templates.
  }
}
