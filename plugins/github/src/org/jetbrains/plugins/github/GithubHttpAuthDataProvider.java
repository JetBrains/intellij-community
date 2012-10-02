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
package org.jetbrains.plugins.github;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.AuthData;
import git4idea.jgit.GitHttpAuthDataProvider;
import git4idea.jgit.GitHttpCredentialsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class GithubHttpAuthDataProvider implements GitHttpAuthDataProvider {

  @Nullable
  @Override
  public AuthData getAuthData(@NotNull String url) {
    if (!GithubUtil.isGithubUrl(url)) {
      return null;
    }

    String login = GithubSettings.getInstance().getLogin();
    if (StringUtil.isEmptyOrSpaces(login)) {
      return null;
    }

    String key = GithubSettings.GITHUB_SETTINGS_PASSWORD_KEY;
    try {
      return new AuthData(login, PasswordSafe.getInstance().getPassword(null, GithubSettings.class, key));
    }
    catch (PasswordSafeException e) {
      GithubUtil.LOG.info("Couldn't get the password for key [" + key + "]", e);
      return null;
    }
  }

}
