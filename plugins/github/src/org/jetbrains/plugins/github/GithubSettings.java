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
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.jetbrains.plugins.github.GithubAuthData.AuthType;

/**
 * @author oleg
 */
@SuppressWarnings("MethodMayBeStatic")
@State(
  name = "GithubSettings",
  storages = {@Storage(
    file = StoragePathMacros.APP_CONFIG + "/github_settings.xml")})
public class GithubSettings implements PersistentStateComponent<GithubSettings.State> {
  private static final Logger LOG = GithubUtil.LOG;
  private static final String GITHUB_SETTINGS_PASSWORD_KEY = "GITHUB_SETTINGS_PASSWORD_KEY";

  private State myState = new State();

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  public static class State {
    public String LOGIN = "";
    public String HOST = GithubApiUtil.DEFAULT_GITHUB_HOST;
    public AuthType AUTH_TYPE = AuthType.BASIC;
    public boolean ANONYMOUS_GIST = false;
    public boolean OPEN_IN_BROWSER_GIST = true;
    public boolean PRIVATE_GIST = true;
    public Collection<String> TRUSTED_HOSTS = new ArrayList<String>();
  }

  public static GithubSettings getInstance() {
    return ServiceManager.getService(GithubSettings.class);
  }

  @NotNull
  public String getHost() {
    return myState.HOST;
  }

  // TODO return null if no login instead of empty string
  @NotNull
  public String getLogin() {
    return StringUtil.notNullize(myState.LOGIN);
  }

  @NotNull
  public AuthType getAuthType() {
    return myState.AUTH_TYPE;
  }

  public void setHost(@NotNull String host) {
    myState.HOST = StringUtil.notNullize(host, GithubApiUtil.DEFAULT_GITHUB_HOST);
  }

  public void setLogin(@NotNull String login) {
    myState.LOGIN = login;
  }

  public void setAuthType(@NotNull AuthType authType) {
    myState.AUTH_TYPE = authType;
  }

  public boolean isAnonymousGist() {
    return myState.ANONYMOUS_GIST;
  }

  public boolean isOpenInBrowserGist() {
    return myState.OPEN_IN_BROWSER_GIST;
  }

  public boolean isPrivateGist() {
    return myState.PRIVATE_GIST;
  }

  public void setAnonymousGist(final boolean anonymousGist) {
    myState.ANONYMOUS_GIST = anonymousGist;
  }

  public void setPrivateGist(final boolean privateGist) {
    myState.PRIVATE_GIST = privateGist;
  }

  public void setOpenInBrowserGist(final boolean openInBrowserGist) {
    myState.OPEN_IN_BROWSER_GIST = openInBrowserGist;
  }

  @NotNull
  public Collection<String> getTrustedHosts() {
    return myState.TRUSTED_HOSTS;
  }

  public void addTrustedHost(String host) {
    if (!myState.TRUSTED_HOSTS.contains(host)) {
      myState.TRUSTED_HOSTS.add(host);
    }
  }

  @NotNull
  private String getPassword() {
    String password;
    try {
      password = PasswordSafe.getInstance().getPassword(null, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't get password for key [" + GITHUB_SETTINGS_PASSWORD_KEY + "]", e);
      password = "";
    }

    return StringUtil.notNullize(password);
  }

  private void setPassword(@NotNull String password, boolean rememberPassword) {
    try {
      if (rememberPassword) {
        PasswordSafe.getInstance().storePassword(null, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY, password);
      }
      else {
        final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
        if (passwordSafe.getSettings().getProviderType() != PasswordSafeSettings.ProviderType.DO_NOT_STORE) {
          passwordSafe.getMemoryProvider().storePassword(null, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY, password);
        }
      }
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't set password for key [" + GITHUB_SETTINGS_PASSWORD_KEY + "]", e);
    }
  }

  @NotNull
  public GithubAuthData getAuthData() {
    switch (getAuthType()) {
      case BASIC:
        return GithubAuthData.createBasicAuth(getHost(), getLogin(), getPassword());
      case TOKEN:
        return GithubAuthData.createTokenAuth(getHost(), getLogin(), getPassword());
      case ANONYMOUS:
      default:
        throw new IllegalStateException("GithubSettings: getAuthData - wrong AuthType: " + getAuthType());
    }
  }

  public void setAuthData(@NotNull GithubAuthData auth, boolean rememberPassword) {
    setHost(auth.getHost());
    setLogin(auth.getLogin());
    setAuthType(auth.getAuthType());

    switch (auth.getAuthType()) {
      case BASIC:
        assert auth.getBasicAuth() != null;
        setPassword(auth.getBasicAuth().getPassword(), rememberPassword);
        break;
      case TOKEN:
        assert auth.getTokenAuth() != null;
        setPassword(auth.getTokenAuth().getToken(), rememberPassword);
        break;
      case ANONYMOUS:
      default:
        throw new IllegalStateException("GithubSettings: setAuthData - wrong AuthType: " + getAuthType());
    }
  }
}