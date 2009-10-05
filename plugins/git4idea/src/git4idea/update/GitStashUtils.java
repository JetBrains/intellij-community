package git4idea.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitVersion;
import org.jetbrains.annotations.NotNull;

/**
 * The class contains utilities for creating and removing stashes.
 */
public class GitStashUtils {
  /**
   * The version when quiet stash supported
   */
  private final static GitVersion QUIET_STASH_SUPPORTED = new GitVersion(1, 6, 4, 0);

  private GitStashUtils() {
  }

  /**
   * Create stash for later use
   *
   * @param project the project to use
   * @param root    the root
   * @param message the message for the stash
   * @return true if the stash was created, false otherwise
   */
  public static boolean saveStash(@NotNull Project project, @NotNull VirtualFile root, final String message) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.STASH);
    handler.setNoSSH(true);
    handler.addParameters("save", message);
    String output = handler.run();
    return !output.startsWith("No local changes to save");
  }

  /**
   * Create stash for later use
   *
   * @param project the project to use
   * @param root    the root
   */
  public static void popLastStash(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.STASH);
    handler.setNoSSH(true);
    handler.addParameters("pop");
    if(QUIET_STASH_SUPPORTED.isLessOrEqual(GitVcs.getInstance(project).version())) {
      handler.addParameters("--quiet");
    }
    handler.run();
  }
}
