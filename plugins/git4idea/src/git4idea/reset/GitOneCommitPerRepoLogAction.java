package git4idea.reset;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.VcsLogOneCommitPerRepoAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GitOneCommitPerRepoLogAction extends VcsLogOneCommitPerRepoAction<GitRepository> {

  @NotNull
  @Override
  protected AbstractRepositoryManager<GitRepository> getRepositoryManager(@NotNull Project project) {
    return GitRepositoryManager.getInstance(project);
  }

  @Nullable
  @Override
  protected GitRepository getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root) {
    return getRepositoryManager(project).getRepositoryForRoot(root);
  }
}
