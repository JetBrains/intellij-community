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

import com.intellij.ide.passwordSafe.MasterPasswordUnavailableException;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.MasterKeyPasswordSafe;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
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
  private static final String ANONIMOUS_GIST = "Anonymous";
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
  private Collection<String> myTrustedHosts = new ArrayList<String>();

  private static final Logger LOG = Logger.getInstance(GithubSettings.class.getName());
  private boolean passwordChanged = false;

  // Once master password is refused, do not ask for it again
  private boolean masterPasswordRefused = false;


  public static GithubSettings getInstance(){
    return ServiceManager.getService(GithubSettings.class);
  }

  public Element getState() {
    LOG.assertTrue(!ProgressManager.getInstance().hasProgressIndicator(), "Password should not be accessed under modal progress");

    try {
      if (passwordChanged && !masterPasswordRefused) {
        PasswordSafe.getInstance().storePassword(null, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY, getPassword());
      }
    }
    catch (MasterPasswordUnavailableException e){
      LOG.info("Couldn't store password for key [" + GITHUB_SETTINGS_PASSWORD_KEY + "]", e);
      masterPasswordRefused = true;
    }
    catch (Exception e) {
      Messages.showErrorDialog("Error happened while storing password for github", "Error");
      LOG.info("Couldn't get password for key [" + GITHUB_SETTINGS_PASSWORD_KEY + "]", e);
    }
    passwordChanged = false;
    final Element element = new Element(GITHUB_SETTINGS_TAG);
    element.setAttribute(LOGIN, getLogin());
    element.setAttribute(HOST, getHost());
    element.setAttribute(ANONIMOUS_GIST, String.valueOf(isAnonymous()));
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
      setAnonymousGist(Boolean.valueOf(element.getAttributeValue(ANONIMOUS_GIST)));
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
    return myLogin != null ? myLogin : "";
  }

  @NotNull
  public String getPassword() {
    LOG.assertTrue(!ProgressManager.getInstance().hasProgressIndicator(), "Password should not be accessed under modal progress");
    String password;
    final Project project = ProjectManager.getInstance().getDefaultProject();
    final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
    try {
      password = passwordSafe.getMemoryProvider().getPassword(project, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY);
      if (password != null) {
        return password;
      }
      final MasterKeyPasswordSafe masterKeyProvider = passwordSafe.getMasterKeyProvider();
      if (!masterKeyProvider.isEmpty()) {
        // workaround for: don't ask for master password, if the requested password is not there.
        // this should be fixed in PasswordSafe: don't ask master password to look for keys
        // until then we assume that is PasswordSafe was used (there is anything there), then it makes sense to look there.
        password = masterKeyProvider.getPassword(project, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY);
      }
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't get password for key [" + GITHUB_SETTINGS_PASSWORD_KEY + "]", e);
      masterPasswordRefused = true;
      password = "";
    }

    passwordChanged = false;
    return password != null ? password : "";
  }

  public String getHost() {
    return myHost != null ? myHost : GithubApiUtil.DEFAULT_GITHUB_HOST;
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
    myLogin = login != null ? login : "";
  }

  public void setPassword(final String password) {
    passwordChanged = !getPassword().equals(password);
    try {
      PasswordSafe.getInstance().storePassword(null, GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY, password != null ? password : "");
    }
    catch (PasswordSafeException e) {
      LOG.info("Couldn't get password for key [" + GITHUB_SETTINGS_PASSWORD_KEY + "]", e);
    }
  }

  public void setHost(final String host) {
    myHost = host != null ? host : GithubApiUtil.DEFAULT_GITHUB_HOST;
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
}