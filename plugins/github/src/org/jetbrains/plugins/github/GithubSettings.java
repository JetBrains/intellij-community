package org.jetbrains.plugins.github;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

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
  private static final String PASSWORD = "Password";
  private static final String HOST = "Host";
  private static final String GITHUB = "github.com";

  private String myLogin;
  private String myPassword;
  private String myHost;

  public static GithubSettings getInstance(){
    return ServiceManager.getService(GithubSettings.class);
  }

  public Element getState() {
    if (StringUtil.isEmptyOrSpaces(myLogin) && StringUtil.isEmptyOrSpaces(myPassword) && StringUtil.isEmptyOrSpaces(myHost)) {
      return null;
    }
    final Element element = new Element(GITHUB_SETTINGS_TAG);
    element.setAttribute(LOGIN, getLogin());
    element.setAttribute(PASSWORD, getEncodedPassword());
    element.setAttribute(HOST, getHost());
    return element;
  }

  public String getEncodedPassword() {
    return PasswordUtil.encodePassword(getPassword());
  }

  public void setEncodedPassword(final String password) {
    try {
      setPassword(PasswordUtil.decodePassword(password));
    }
    catch (NumberFormatException e) {
      // do nothing
    }
  }

  public void loadState(@NotNull final Element element) {
    try {
      setLogin(element.getAttributeValue(LOGIN));
      setEncodedPassword(element.getAttributeValue(PASSWORD));
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

  @NotNull
  public String getPassword() {
    return myPassword != null ? myPassword : "";
  }

  public String getHost() {
    return myHost != null ? myHost : GITHUB;
  }

  public void setLogin(final String login) {
    myLogin = login != null ? login : "";
  }

  public void setPassword(final String password) {
    myPassword = password != null ? password : "";
  }

  public void setHost(final String host) {
    myHost = host != null ? host : GITHUB;
  }

}