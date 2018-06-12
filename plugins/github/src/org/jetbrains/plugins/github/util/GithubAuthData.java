/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Container for authentication data:
 * * host
 * * credentials
 *    - login/password pair
 *        or
 *    - login/password pair/2 factor code
 *        or
 *    - OAuth2 access token
 *
 * @author Aleksey Pivovarov
 */
public class GithubAuthData {
  public enum AuthType {BASIC, TOKEN, ANONYMOUS}

  @NotNull private final AuthType myAuthType;
  @NotNull private final String myHost;
  @Nullable private final BasicAuth myBasicAuth;
  @Nullable private final TokenAuth myTokenAuth;
  private final boolean myUseProxy;

  private GithubAuthData(@NotNull AuthType authType,
                         @NotNull String host,
                         @Nullable BasicAuth basicAuth,
                         @Nullable TokenAuth tokenAuth,
                         boolean useProxy) {
    myAuthType = authType;
    myHost = host;
    myBasicAuth = basicAuth;
    myTokenAuth = tokenAuth;
    myUseProxy = useProxy;
  }

  @Deprecated
  public static GithubAuthData createAnonymous() {
    return createAnonymous(StringUtil.notNullize(GithubSettings.getInstance().getHost()));
  }

  public static GithubAuthData createAnonymous(@NotNull String host) {
    return new GithubAuthData(AuthType.ANONYMOUS, host, null, null, true);
  }

  public static GithubAuthData createBasicAuth(@NotNull String host, @NotNull String login, @NotNull String password) {
    return new GithubAuthData(AuthType.BASIC, host, new BasicAuth(login, password), null, true);
  }

  public static GithubAuthData createBasicAuthTF(@NotNull String host,
                                                 @NotNull String login,
                                                 @NotNull String password,
                                                 @NotNull String code) {
    return new GithubAuthData(AuthType.BASIC, host, new BasicAuth(login, password, code), null, true);
  }

  public static GithubAuthData createTokenAuth(@NotNull String host, @NotNull String token) {
    return new GithubAuthData(AuthType.TOKEN, host, null, new TokenAuth(token), true);
  }

  public static GithubAuthData createTokenAuth(@NotNull String host, @NotNull String token, boolean useProxy) {
    return new GithubAuthData(AuthType.TOKEN, host, null, new TokenAuth(token), useProxy);
  }

  @NotNull
  public AuthType getAuthType() {
    return myAuthType;
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

  public boolean isUseProxy() {
    return myUseProxy;
  }

  @NotNull
  public GithubAuthData copyWithTwoFactorCode(@NotNull String code) {
    if (myBasicAuth == null) {
      throw new IllegalStateException("Two factor authentication can be used only with Login/Password");
    }

    return createBasicAuthTF(getHost(), myBasicAuth.getLogin(), myBasicAuth.getPassword(), code);
  }

  public static class BasicAuth {
    @NotNull private final String myLogin;
    @NotNull private final String myPassword;
    @Nullable private final String myCode;

    private BasicAuth(@NotNull String login, @NotNull String password) {
      this(login, password, null);
    }

    private BasicAuth(@NotNull String login, @NotNull String password, @Nullable String code) {
      myLogin = login;
      myPassword = password;
      myCode = code;
    }

    @NotNull
    public String getLogin() {
      return myLogin;
    }

    @NotNull
    public String getPassword() {
      return myPassword;
    }

    @Nullable
    public String getCode() {
      return myCode;
    }
  }

  public static class TokenAuth {
    @NotNull private final String myToken;

    private TokenAuth(@NotNull String token) {
      myToken = StringUtil.trim(token);
    }

    @NotNull
    public String getToken() {
      return myToken;
    }
  }
}
