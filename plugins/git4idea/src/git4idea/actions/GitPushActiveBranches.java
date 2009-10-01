package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.checkin.GitPushActiveBranchesDialog;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * The action that pushes active branches
 */
public class GitPushActiveBranches extends GitRepositoryAction{
  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("push.active.action.name");
  }

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    GitPushActiveBranchesDialog.showDialog(project, gitRoots, exceptions);
  }
}
