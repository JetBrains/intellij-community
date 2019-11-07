package com.intellij.jps.cache.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class GitRepositoryUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.git.GitRepositoryUtil");

  private GitRepositoryUtil() {}

  public static Iterator<String> getCommitsIterator(@NotNull Project project) {
    if (GitUtil.hasGitRepositories(project)) {
      VirtualFile virtualFile = project.getProjectFile();
      if (virtualFile == null) return Collections.emptyIterator();

      GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(virtualFile);
      if (repository == null) return Collections.emptyIterator();
      return new GitCommitsIterator(project, repository);
    }
    LOG.debug("Project doesn't contain Git repository");
    return Collections.emptyIterator();
  }

  private static class GitCommitsIterator implements Iterator<String> {
    private static final int MAX_FETCH_SIZE = 5000;
    private static final int FETCH_SIZE = 100;
    private final GitRepository myRepository;
    private final Project myProject;
    private int fetchedCount;
    private List<String> commits;
    private int currentPosition;

    private GitCommitsIterator(@NotNull Project project, @NotNull GitRepository repository) {
      myRepository = repository;
      myProject = project;
      fetchedCount = 0;
      fetchOldCommits();
    }

    @Override
    public boolean hasNext() {
      if (commits.size() > 0) {
        if (currentPosition < commits.size()) return true;
        if (fetchedCount >= MAX_FETCH_SIZE) {
          LOG.warn("Exceeded fetch limit for git commits");
          return false;
        }
        fetchOldCommits(commits.get(currentPosition - 1));
        if (commits.size() > 0) {
          currentPosition = 0;
          return true;
        }
      }
      return false;
    }

    @Override
    public String next() {
      if (commits.size() == 0 || currentPosition >= commits.size()) throw new NoSuchElementException();
      String result = commits.get(currentPosition);
      currentPosition++;
      return result;
    }

    private void fetchOldCommits() {
      fetchOldCommits("");
    }

    private void fetchOldCommits(String latestCommit) {
      try {
        commits = ContainerUtil.map(latestCommit.isEmpty() ? GitHistoryUtils.history(myProject, myRepository.getRoot(), "-n " + FETCH_SIZE) :
                            GitHistoryUtils.history(myProject, myRepository.getRoot(), latestCommit, "-n " + FETCH_SIZE),
                            it -> it.getId().asString());
        fetchedCount += commits.size();
        return;
      }
      catch (VcsException e) {
        LOG.warn("Can't get Git hashes for commits", e);
      }
      commits = new SmartList<>();
    }
  }
}
