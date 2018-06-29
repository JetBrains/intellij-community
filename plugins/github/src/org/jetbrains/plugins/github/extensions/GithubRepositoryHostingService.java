// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions;

import com.intellij.dvcs.hosting.RepositoryListLoader;
import com.intellij.dvcs.hosting.RepositoryListLoadingException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.DialogManager;
import git4idea.remote.GitRepositoryHostingService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;
import org.jetbrains.plugins.github.util.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GithubRepositoryHostingService extends GitRepositoryHostingService {
  @NotNull
  @Override
  public String getServiceDisplayName() {
    return "GitHub";
  }

  @Override
  @NotNull
  public RepositoryListLoader getRepositoryListLoader(@NotNull Project project) {
    return new RepositoryListLoader() {
      @NotNull private final GithubAuthDataHolder myAuthDataHolder = GithubAuthDataHolder.createFromSettings();

      @Override
      public boolean isEnabled() {
        GithubAuthData authData = myAuthDataHolder.getAuthData();
        if (authData.getAuthType().equals(GithubAuthData.AuthType.BASIC)) {
          GithubAuthData.BasicAuth basicAuth = authData.getBasicAuth();
          if (basicAuth == null) return false;
          return StringUtil.isNotEmpty(basicAuth.getPassword());
        }
        if (authData.getAuthType().equals(GithubAuthData.AuthType.TOKEN)) {
          GithubAuthData.TokenAuth tokenAuth = authData.getTokenAuth();
          if (tokenAuth == null) return false;
          return StringUtil.isNotEmpty(tokenAuth.getToken());
        }
        return false;
      }

      @Override
      public boolean enable() {
        GithubAuthData currentAuthData = myAuthDataHolder.getAuthData();
        myAuthDataHolder.runTransaction(currentAuthData, () -> {
          GithubLoginDialog dialog = new GithubLoginDialog(project, currentAuthData, AuthLevel.LOGGED);
          DialogManager.show(dialog);
          if (dialog.isOK()) {
            GithubAuthData authData = dialog.getAuthData();
            GithubSettings.getInstance().setAuthData(authData, dialog.isSavePasswordSelected());
            return authData;
          }
          return currentAuthData;
        });
        return isEnabled();
      }

      @NotNull
      @Override
      public List<String> getAvailableRepositories(@NotNull ProgressIndicator progressIndicator) throws RepositoryListLoadingException {
        try {
          return GithubUtil.runTask(project, myAuthDataHolder, progressIndicator, connection -> GithubApiUtil.getAvailableRepos(connection))
                           .stream()
                           .sorted(Comparator.comparing(GithubRepo::getUserName).thenComparing(GithubRepo::getName))
                           .map(repo -> GithubUrlUtil
                             .getCloneUrl(GithubUrlUtil.getGitHostWithoutProtocol(myAuthDataHolder.getAuthData().getHost()),
                                          repo.getUserName(),
                                          repo.getName()))
                           .collect(Collectors.toList());
        }
        catch (Exception e) {
          throw new RepositoryListLoadingException("Error connecting to Github", e);
        }
      }
    };
  }
}
