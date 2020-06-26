package com.intellij.jps.cache.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class GitRepositoryUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.git.GitRepositoryUtil");

  private GitRepositoryUtil() {}

  public static List<Iterator<String>> getCommitsIterator(@NotNull Project project) {
    if (GitUtil.hasGitRepositories(project)) {
      Collection<GitRepository> projectRepositories = GitUtil.getRepositories(project);
      return ContainerUtil.map(projectRepositories, repo -> new GitCommitsIterator(project, repo));
    }
    LOG.info("Project doesn't contain Git repository");
    return Collections.emptyList();
  }

  private static final class GitCommitsIterator implements Iterator<String> {
    private static final int MAX_FETCH_SIZE = 1000;
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
          LOG.info("Exceeded fetch limit for git commits");
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
