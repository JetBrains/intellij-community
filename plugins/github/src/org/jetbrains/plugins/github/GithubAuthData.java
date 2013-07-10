/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Container for authentication data: host, login and password.
 *
 * @author Aleksey Pivovarov
 */
public class GithubAuthData {
  private @NotNull final String myHost;
  private @Nullable final BasicAuth myBasicAuth;
  private @Nullable final TokenAuth myTokenAuth;

  private GithubAuthData(@NotNull String host, @Nullable BasicAuth basicAuth, @Nullable TokenAuth tokenAuth) {
    myHost = host;
    myBasicAuth = basicAuth;
    myTokenAuth = tokenAuth;
  }

  public static GithubAuthData createAnonymous(@NotNull String host) {
    return new GithubAuthData(host, null, null);
  }

  public static GithubAuthData createBasicAuth(@NotNull String host, @NotNull String login, @NotNull String password) {
    return new GithubAuthData(host, new BasicAuth(login, password), null);
  }

  public static GithubAuthData createTokenAuth(@NotNull String host, @NotNull String token) {
    return new GithubAuthData(host, null, new TokenAuth(token));
  }

  @NotNull
  public String getHost() {
    return myHost;
  }

  @Nullable
  public BasicAuth getBasicAuth() {
    return myBasicAuth;
  }

  @Nullable
  public TokenAuth getTokenAuth() {
    return myTokenAuth;
  }

  public static class BasicAuth {
    private @NotNull final String myLogin;
    private @NotNull final String myPassword;

    private BasicAuth(@NotNull String login, @NotNull String password) {
      myLogin = login;
      myPassword = password;
    }

    @NotNull
    String getLogin() {
      return myLogin;
    }

    @NotNull
    String getPassword() {
      return myPassword;
    }
  }

  public static class TokenAuth {
    private @NotNull final String myToken;

    private TokenAuth(@NotNull String token) {
      myToken = token;
    }

    @NotNull
    String getToken() {
      return myToken;
    }
  }
}
