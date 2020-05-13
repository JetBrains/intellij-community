// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions;

import com.intellij.dvcs.hosting.RepositoryListLoader;
import com.intellij.dvcs.hosting.RepositoryListLoadingException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.remote.GitHttpAuthDataProvider;
import git4idea.remote.GitRepositoryHostingService;
import git4idea.remote.InteractiveGitHttpAuthDataProvider;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper;
import org.jetbrains.plugins.github.util.GithubGitHelper;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class GithubRepositoryHostingService extends GitRepositoryHostingService {
  @NotNull private final GithubAuthenticationManager myAuthenticationManager;
  @NotNull private final GithubApiRequestExecutorManager myExecutorManager;
  @NotNull private final GithubGitHelper myGitHelper;
  @NotNull private final GithubHttpAuthDataProvider myAuthDataProvider;

  public GithubRepositoryHostingService() {
    myAuthenticationManager = GithubAuthenticationManager.getInstance();
    myExecutorManager = GithubApiRequestExecutorManager.getInstance();
    myGitHelper = GithubGitHelper.getInstance();
    myAuthDataProvider = GitHttpAuthDataProvider.EP_NAME.findExtensionOrFail(GithubHttpAuthDataProvider.class);
  }

  @NotNull
  @Override
  public String getServiceDisplayName() {
    return GithubUtil.SERVICE_DISPLAY_NAME;
  }

  @Override
  @NotNull
  public RepositoryListLoader getRepositoryListLoader(@NotNull Project project) {
    return new RepositoryListLoader() {
      @NotNull private final Map<GithubAccount, GithubApiRequestExecutor> myExecutors = new HashMap<>();

      @Override
      public boolean isEnabled() {
        for (GithubAccount account : myAuthenticationManager.getAccounts()) {
          try {
            myExecutors.put(account, myExecutorManager.getExecutor(account));
          }
          catch (GithubMissingTokenException e) {
            // skip
          }
        }
        return !myExecutors.isEmpty();
      }

      @Override
      public boolean enable(@Nullable Component parentComponent) {
        if (!GithubAccountsMigrationHelper.getInstance().migrate(project, parentComponent)) return false;
        if (!myAuthenticationManager.ensureHasAccounts(project, parentComponent)) return false;
        boolean atLeastOneHasToken = false;
        for (GithubAccount account : myAuthenticationManager.getAccounts()) {
          GithubApiRequestExecutor executor = myExecutorManager.getExecutor(account, project);
          if (executor == null) continue;
          myExecutors.put(account, executor);
          atLeastOneHasToken = true;
        }
        return atLeastOneHasToken;
      }

      @NotNull
      @Override
      public Result getAvailableRepositoriesFromMultipleSources(@NotNull ProgressIndicator progressIndicator) {
        List<String> urls = new ArrayList<>();
        List<RepositoryListLoadingException> exceptions = new ArrayList<>();

        for (Map.Entry<GithubAccount, GithubApiRequestExecutor> entry : myExecutors.entrySet()) {
          GithubServerPath server = entry.getKey().getServer();
          GithubApiRequestExecutor executor = entry.getValue();
          try {
            Stream<GithubRepo> streamAssociated = GithubApiPagesLoader
              .loadAll(executor, progressIndicator, GithubApiRequests.CurrentUser.Repos.pages(server)).stream();

            Stream<GithubRepo> streamWatched = StreamEx.empty();
            try {
              streamWatched = GithubApiPagesLoader
                .loadAll(executor, progressIndicator, GithubApiRequests.CurrentUser.RepoSubs.pages(server)).stream();
            }
            catch (GithubAuthenticationException | GithubStatusCodeException ignore) {
              // We already can return something useful from getUserRepos, so let's ignore errors.
              // One of this may not exist in GitHub enterprise
            }
            urls.addAll(
              Stream.concat(streamAssociated, streamWatched)
                .sorted(Comparator.comparing(GithubRepo::getUserName).thenComparing(GithubRepo::getName))
                .map(repo -> myGitHelper.getRemoteUrl(server, repo.getUserName(), repo.getName()))
                .collect(Collectors.toList())
            );
          }
          catch (Exception e) {
            exceptions.add(new RepositoryListLoadingException("Cannot load repositories from GitHub", e));
          }
        }
        return new Result(urls, exceptions);
      }
    };
  }

  @CalledInBackground
  @Nullable
  @Override
  public InteractiveGitHttpAuthDataProvider getInteractiveAuthDataProvider(@NotNull Project project, @NotNull String url) {
    return getProvider(project, url, null);
  }

  @CalledInBackground
  @Nullable
  @Override
  public InteractiveGitHttpAuthDataProvider getInteractiveAuthDataProvider(@NotNull Project project,
                                                                           @NotNull String url,
                                                                           @NotNull String login) {
    return getProvider(project, url, login);
  }

  @Nullable
  private InteractiveGitHttpAuthDataProvider getProvider(@NotNull Project project, @NotNull String url, @Nullable String login) {
    Set<GithubAccount> potentialAccounts = myAuthDataProvider.getSuitableAccounts(project, url, login);
    if (!potentialAccounts.isEmpty()) {
      return new InteractiveSelectGithubAccountHttpAuthDataProvider(project, potentialAccounts, myAuthenticationManager);
    }

    if (GithubServerPath.DEFAULT_SERVER.matches(url)) {
      return new InteractiveCreateGithubAccountHttpAuthDataProvider(project, myAuthenticationManager,
                                                                    GithubServerPath.DEFAULT_SERVER, login);
    }

    return null;
  }
}
