// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions;

import com.intellij.dvcs.hosting.RepositoryListLoader;
import com.intellij.dvcs.hosting.RepositoryListLoadingException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.remote.GitRepositoryHostingService;
import org.jetbrains.annotations.NotNull;
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
import java.util.stream.Collectors;

public class GithubRepositoryHostingService extends GitRepositoryHostingService {
  @NotNull private final GithubAuthenticationManager myAuthenticationManager;
  @NotNull private final GithubGitHelper myGitHelper;

  public GithubRepositoryHostingService(@NotNull GithubAuthenticationManager manager,
                                        @NotNull GithubGitHelper gitHelper) {
    myAuthenticationManager = manager;
    myGitHelper = gitHelper;
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
        return myAuthenticationManager.hasAccounts();
      }

      @Override
      public boolean enable() {
        if (!GithubAccountsMigrationHelper.getInstance().migrate(project)) return false;
        return myAuthenticationManager.ensureHasAccounts(project);
      }

      @NotNull
      @Override
      public List<String> getAvailableRepositories(@NotNull ProgressIndicator progressIndicator) throws RepositoryListLoadingException {
        try {
          List<String> urls = new ArrayList<>();
          for (GithubAccount account : myAuthenticationManager.getAccounts()) {
            urls.addAll(
              GithubApiTaskExecutor.getInstance().execute(progressIndicator, account,
                                                          connection -> GithubApiUtil.getAvailableRepos(connection))
                                   .stream()
                                   .sorted(Comparator.comparing(GithubRepo::getUserName).thenComparing(GithubRepo::getName))
                                   .map(repo -> myGitHelper.getRemoteUrl(account.getServer(),
                                                                         repo.getUserName(),
                                                                         repo.getName()))
                                   .collect(Collectors.toList())
            );
          }
          return urls;
        }
        catch (Exception e) {
          throw new RepositoryListLoadingException("Error connecting to Github", e);
        }
      }
    };
  }
}
