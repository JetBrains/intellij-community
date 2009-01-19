package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitHandler;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorMain;
import git4idea.rebase.GitRebaseEditorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The base class for rebase actions that use editor
 */
public abstract class GitRebaseActionBase extends GitRepositoryAction {
  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    GitLineHandler h = createHandler(project, gitRoots, defaultRoot);
    final VirtualFile root = h.workingDirectoryFile();
    GitRebaseEditorService service = GitRebaseEditorService.getInstance();
    GitRebaseEditorHandler editor = service.getHandler(project, root);
    configureEditor(editor);
    affectedRoots.add(root);
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

  /**
   * This method could be overrriden to supply addtional information to the editor.
   *
   * @param editor
   */
  protected void configureEditor(GitRebaseEditorHandler editor) {
  }

  /**
   * Create line handler that represents a git operation
   *
   * @param project     the context project
   * @param gitRoots    the git roots
   * @param defaultRoot the default root
   * @return the line handler or null
   */
  @Nullable
  protected abstract GitLineHandler createHandler(Project project, List<VirtualFile> gitRoots, VirtualFile defaultRoot);
}
