// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions;

import com.intellij.dvcs.hosting.RepositoryListLoader;
import com.intellij.dvcs.hosting.RepositoryListLoadingException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import git4idea.remote.GitRepositoryHostingService;
import git4idea.remote.InteractiveGitHttpAuthDataProvider;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiTaskExecutor;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper;
import org.jetbrains.plugins.github.util.GithubGitHelper;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GithubRepositoryHostingService extends GitRepositoryHostingService {
  @NotNull private final GithubAuthenticationManager myAuthenticationManager;
  @NotNull private final GithubApiTaskExecutor myApiTaskExecutor;
  @NotNull private final GithubGitHelper myGitHelper;
  @NotNull private final GithubHttpAuthDataProvider myAuthDataProvider;

  public GithubRepositoryHostingService(@NotNull GithubAuthenticationManager manager,
                                        @NotNull GithubApiTaskExecutor executor,
                                        @NotNull GithubGitHelper gitHelper,
                                        @NotNull GithubHttpAuthDataProvider authDataProvider) {
    myAuthenticationManager = manager;
    myApiTaskExecutor = executor;
    myGitHelper = gitHelper;
    myAuthDataProvider = authDataProvider;
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
      @Override
      public boolean isEnabled() {
        for (GithubAccount account: myAuthenticationManager.getAccounts()) {
          if (myAuthenticationManager.hasTokenForAccount(account)) return true;
        }
        return false;
      }

      @Override
      public boolean enable() {
        if (!GithubAccountsMigrationHelper.getInstance().migrate(project)) return false;
        return myAuthenticationManager.ensureHasAccountsWithTokens(project);
      }

      @NotNull
      @Override
      public Pair<List<String>, List<RepositoryListLoadingException>> getAvailableRepositoriesFromMultipleSources(@NotNull ProgressIndicator progressIndicator) {
        List<String> urls = new ArrayList<>();
        List<RepositoryListLoadingException> exceptions = new ArrayList<>();

        for (GithubAccount account: myAuthenticationManager.getAccounts()) {
          try {
            urls.addAll(
              myApiTaskExecutor.execute(progressIndicator, account, connection -> GithubApiUtil.getAvailableRepos(connection), true)
                               .stream()
                               .sorted(Comparator.comparing(GithubRepo::getUserName).thenComparing(GithubRepo::getName))
                               .map(repo -> myGitHelper.getRemoteUrl(account.getServer(),
                                                                     repo.getUserName(),
                                                                     repo.getName()))
                               .collect(Collectors.toList())
            );
          }
          catch (Exception e) {
            exceptions.add(new RepositoryListLoadingException("Cannot load repositories from Github", e));
          }
        }
        return Pair.create(urls, exceptions);
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
    if (potentialAccounts.isEmpty()) return null;
    return new InteractiveGithubHttpAuthDataProvider(project, potentialAccounts, myAuthenticationManager);
  }
}
