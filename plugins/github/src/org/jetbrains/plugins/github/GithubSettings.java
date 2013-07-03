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
import com.intellij.ide.passwordSafe.config.PasswordSafeConfigurable;
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.impl.providers.memory.MemoryPasswordSafe;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author oleg
 */
@State(
    name = "GithubSettings",
    storages = {
        @Storage(
            file = StoragePathMacros.APP_CONFIG + "/github_settings.xml"
        )}
)
public class GithubSettings implements PersistentStateComponent<Element> {

  private static final String GITHUB_SETTINGS_TAG = "GithubSettings";
  private static final String LOGIN = "Login";
  private static final String HOST = "Host";
  private static final String ANONYMOUS_GIST = "Anonymous";
  private static final String OPEN_IN_BROWSER_GIST = "OpenInBrowser";
  private static final String PRIVATE_GIST = "Private";
  public static final String GITHUB_SETTINGS_PASSWORD_KEY = "GITHUB_SETTINGS_PASSWORD_KEY";
  private static final String TRUSTED_HOSTS = "GITHUB_TRUSTED_HOSTS";
  private static final String TRUSTED_HOST = "HOST";
  private static final String TRUSTED_URL = "URL";

  private String myLogin;
  private String myHost;
  private boolean myAnonymousGist;
  private boolean myOpenInBrowserGist = true;
  private boolean myPrivateGist;
  private final Collection<String> myTrustedHosts = new ArrayList<String>();

  private static final Logger LOG = Logger.getInstance(GithubSettings.class.getName());

  public static GithubSettings getInstance(){
    return ServiceManager.getService(GithubSettings.class);
  }

  public Element getState() {
    final Element element = new Element(GITHUB_SETTINGS_TAG);
    element.setAttribute(LOGIN, getLogin());
    element.setAttribute(HOST, getHost());
    element.setAttribute(ANONYMOUS_GIST, String.valueOf(isAnonymous()));
    element.setAttribute(PRIVATE_GIST, String.valueOf(isPrivateGist()));
    element.setAttribute(OPEN_IN_BROWSER_GIST, String.valueOf(isOpenInBrowserGist()));
    Element trustedHosts = new Element(TRUSTED_HOSTS);
    for (String host : myTrustedHosts) {
      Element hostEl = new Element(TRUSTED_HOST);
      hostEl.setAttribute(TRUSTED_URL, host);
      trustedHosts.addContent(hostEl);
    }
    element.addContent(trustedHosts);
    return element;
  }

  public void loadState(@NotNull final Element element) {
    // All the logic on retrieving password was moved to getPassword action to cleanup initialization process
    try {
      setLogin(element.getAttributeValue(LOGIN));
      setHost(element.getAttributeValue(HOST));
      setAnonymousGist(Boolean.valueOf(element.getAttributeValue(ANONYMOUS_GIST)));
      setPrivateGist(Boolean.valueOf(element.getAttributeValue(PRIVATE_GIST)));
      setOpenInBrowserGist(Boolean.valueOf(element.getAttributeValue(OPEN_IN_BROWSER_GIST)));
      for (Object trustedHost : element.getChildren(TRUSTED_HOSTS)) {
        addTrustedHost(trustedHost.toString());
      }
    }
    catch (Exception e) {
      LOG.error("Error happened while loading github settings: " + e);
    }
  }

  // TODO return null if no login instead of empty string
  @NotNull
  public String getLogin() {
    return StringUtil.notNullize(myLogin);
  }

  @NotNull
  public String getPassword() {
    String password;
    final Project project = ProjectManager.getInstance().getDefaultProject();
    final PasswordSafe passwordSafe = PasswordSafe.getInstance();
    try {
      password = passwordSafe.getPassword(project, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY);
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't get password for key [" + GITHUB_SETTINGS_PASSWORD_KEY + "]", e);
      password = "";
    }

    return StringUtil.notNullize(password);
  }

  public String getHost() {
    return StringUtil.notNullize(myHost, GithubApiUtil.DEFAULT_GITHUB_HOST);
  }

  public boolean isAnonymous() {
    return myAnonymousGist;
  }

  public boolean isOpenInBrowserGist() {
    return myOpenInBrowserGist;
  }

  public boolean isPrivateGist() {
    return myPrivateGist;
  }

  public void setLogin(final String login) {
    myLogin = StringUtil.notNullize(login);
  }

  public void setPassword(final String password) {
    setPassword(password, true);
  }

  public void setPassword(final String password, final boolean rememberPassword) {
    try {
      if (rememberPassword) {
        PasswordSafe.getInstance().storePassword(null, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY, StringUtil.notNullize(password));
      }
      else {
        final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
        if (passwordSafe.getSettings().getProviderType() != PasswordSafeSettings.ProviderType.DO_NOT_STORE) {
          passwordSafe.getMemoryProvider()
            .storePassword(null, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY, StringUtil.notNullize(password));
        }
      }
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't set password for key [" + GITHUB_SETTINGS_PASSWORD_KEY + "]", e);
    }
  }

  public void setHost(final String host) {
    myHost = StringUtil.notNullize(host, GithubApiUtil.DEFAULT_GITHUB_HOST);
  }

  public void setAnonymousGist(final boolean anonymousGist) {
    myAnonymousGist = anonymousGist;
  }

  public void setPrivateGist(final boolean privateGist) {
    myPrivateGist = privateGist;
  }

  public void setOpenInBrowserGist(final boolean openInBrowserGist) {
    myOpenInBrowserGist = openInBrowserGist;
  }

  @NotNull
  public Collection<String> getTrustedHosts() {
    return myTrustedHosts;
  }

  public void addTrustedHost(String host) {
    if (!myTrustedHosts.contains(host)) {
      myTrustedHosts.add(host);
    }
  }

  public void setAuthData(@NotNull GithubAuthData auth, boolean rememberPassword) {
    setHost(auth.getHost());
    setLogin(auth.getLogin());
    setPassword(auth.getPassword(), rememberPassword);
  }

  @NotNull
  public GithubAuthData getAuthData() {
    return new GithubAuthData(getHost(), getLogin(), getPassword());
  }
}