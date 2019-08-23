package com.intellij.jps.cache.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GitRepositoryUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.git.GitRepositoryUtil");

  private GitRepositoryUtil() {}

  public static List<String> getLatestHashes(@NotNull Project project, int commitsCount) {
    if (GitUtil.hasGitRepositories(project)) {
      VirtualFile virtualFile = project.getProjectFile();
      if (virtualFile == null) return ContainerUtil.newSmartList();

      GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(virtualFile);
      if (repository == null) return ContainerUtil.newSmartList();
      try {
        //TODO :: Check hashes from remote branch related to this
        return ContainerUtil.map(GitHistoryUtils.history(project, repository.getRoot(), "-n " + commitsCount), it -> it.getId().asString());
      }
      catch (VcsException e) {
        LOG.warn("Can't get Git hashes for commits", e);
      }
    }
    LOG.debug("Project doesn't contain Git repository");
    return ContainerUtil.newSmartList();
  }
}
