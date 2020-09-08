package com.intellij.jps.cache.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class GitRepositoryUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.git.GitRepositoryUtil");

  private GitRepositoryUtil() {}

  public static List<GitCommitsIterator> getCommitsIterator(@NotNull Project project, @NotNull Set<String> remoteUrlNames) {
    if (GitUtil.hasGitRepositories(project)) {
      return GitUtil.getRepositories(project).stream()
        .map(repo -> {
          Set<String> remoteUrls = repo.getRemotes().stream()
            .map(remote -> remote.getUrls())
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
          String matchedRemoteUrl = ContainerUtil.find(remoteUrls, remoteUrl -> remoteUrlNames.contains(getRemoteRepoName(remoteUrl)));
          if (matchedRemoteUrl == null) return null;
          return new GitCommitsIterator(project, repo, getRemoteRepoName(matchedRemoteUrl));
        }).filter(Objects::nonNull)
        .collect(Collectors.toList());
    }
    LOG.info("Project doesn't contain Git repository");
    return Collections.emptyList();
  }

  public static String getRemoteRepoName(@NotNull String remoteUrl) {
    String[] splittedRemoteUrl = remoteUrl.split("/");
    return splittedRemoteUrl[splittedRemoteUrl.length - 1];
  }
}