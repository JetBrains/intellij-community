/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
@State(
    name = "GithubSettings",
    storages = {
        @Storage(
            id = "main",
            file = "$APP_CONFIG$/github_settings.xml"
        )}
)
public class GithubSettings implements PersistentStateComponent<Element> {

  private static final String GITHUB_SETTINGS_TAG = "GithubSettings";
  private static final String LOGIN = "Login";
  private static final String HOST = "Host";
  private static final String GITHUB = "github.com";
  private static final String GITHUB_SETTINGS_PASSWORD_KEY = "GITHUB_SETTINGS_PASSWORD_KEY";

  private String myLogin;
  private String myHost;
  private static final Logger LOG = Logger.getInstance(GithubSettings.class.getName());
  private String myPassword;

  public static GithubSettings getInstance(){
    return ServiceManager.getService(GithubSettings.class);
  }

  public Element getState() {
    if (StringUtil.isEmptyOrSpaces(myLogin) && StringUtil.isEmptyOrSpaces(myHost)) {
      return null;
    }
    final Element element = new Element(GITHUB_SETTINGS_TAG);
    element.setAttribute(LOGIN, getLogin());
    element.setAttribute(HOST, getHost());
    return element;
  }

  public void loadState(@NotNull final Element element) {
    try {
      setLogin(element.getAttributeValue(LOGIN));
      setHost(element.getAttributeValue(HOST));
    }
    catch (Exception e) {
      // ignore
    }
  }

  @NotNull
  public String getLogin() {
    return myLogin != null ? myLogin : "";
  }

  @Nullable
  public String getPassword() {
    if (myPassword == null){
      try {
        myPassword = PasswordSafe.getInstance().getPassword(ProjectManager.getInstance().getDefaultProject(), GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY);
      }
      catch (Exception e) {
        myPassword = "";
      }
    }
    return myPassword;
  }

  public String getHost() {
    return myHost != null ? myHost : GITHUB;
  }

  public void setLogin(final String login) {
    myLogin = login != null ? login : "";
  }

  public void setPassword(final String password) {
    if (!Comparing.equal(myPassword, password)) {
      try {
        PasswordSafe.getInstance().storePassword(ProjectManager.getInstance().getDefaultProject(), GithubSettings.class, GITHUB_SETTINGS_PASSWORD_KEY, password);
      }
      catch (Exception e) {
        Messages.showErrorDialog("Error happened while storing password for github", "Error");
        LOG.error(e);
      }
    }
    myPassword = password;
  }

  public void setHost(final String host) {
    myHost = host != null ? host : GITHUB;
  }

}