package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Git rebase action
 */
public class GitRebase extends GitRebaseActionBase {

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
  @Nullable
  protected GitLineHandler createHandler(Project project, List<VirtualFile> gitRoots, VirtualFile defaultRoot) {
    GitRebaseDialog dialog = new GitRebaseDialog(project, gitRoots, defaultRoot);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }
    return dialog.handler();
  }
}
