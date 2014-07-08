/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.extensions;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import git4idea.remote.GitHttpAuthDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jetbrains.plugins.github.util.GithubUrlUtil;

/**
 * @author Kirill Likhodedov
 */
public class GithubHttpAuthDataProvider implements GitHttpAuthDataProvider {

  @Nullable
  @Override
  public AuthData getAuthData(@NotNull String url) {
    if (!GithubUrlUtil.isGithubUrl(url)) {
      return null;
    }

    GithubSettings settings = GithubSettings.getInstance();
    if (!settings.isValidGitAuth()) {
      return null;
    }

    String host1 = GithubUrlUtil.getHostFromUrl(settings.getHost());
    String host2 = GithubUrlUtil.getHostFromUrl(url);
    if (!host1.equalsIgnoreCase(host2)) {
      return null;
    }

    GithubAuthData auth = settings.getAuthData();
    switch (auth.getAuthType()) {
      case BASIC:
        GithubAuthData.BasicAuth basicAuth = auth.getBasicAuth();
        assert basicAuth != null;
        if (StringUtil.isEmptyOrSpaces(basicAuth.getLogin()) || StringUtil.isEmptyOrSpaces(basicAuth.getPassword())) {
          return null;
        }
        return new AuthData(basicAuth.getLogin(), basicAuth.getPassword());
      case TOKEN:
        GithubAuthData.TokenAuth tokenAuth = auth.getTokenAuth();
        assert tokenAuth != null;
        if (StringUtil.isEmptyOrSpaces(tokenAuth.getToken())) {
          return null;
        }
        return new AuthData(tokenAuth.getToken(), "x-oauth-basic");
      default:
        return null;
    }
  }

  @Override
  public void forgetPassword(@NotNull String url) {
    GithubSettings.getInstance().setValidGitAuth(false);
  }
}
