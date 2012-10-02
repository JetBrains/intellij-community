package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfigurableProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.GithubSettings;

import javax.swing.*;

/**
 * @author oleg
 */
public class GitHubSettingsConfigurable implements SearchableConfigurable, VcsConfigurableProvider {
  private static final String DEFAULT_PASSWORD_TEXT = "************";
  private GithubSettingsPanel mySettingsPane;
  private final GithubSettings mySettings;

  public GitHubSettingsConfigurable() {
    mySettings = GithubSettings.getInstance();
  }

  public String getDisplayName() {
    return "GitHub";
  }

  public String getHelpTopic() {
    return "settings.github";
  }

  public JComponent createComponent() {
    if (mySettingsPane == null) {
      mySettingsPane = new GithubSettingsPanel(mySettings);
    }
    return mySettingsPane.getPanel();
  }

  public boolean isModified() {
    return mySettingsPane != null && (!Comparing.equal(mySettings.getLogin(), mySettingsPane.getLogin()) ||
                                      isPasswordModified() ||
           !Comparing.equal(mySettings.getHost(), mySettingsPane.getHost()));
  }

  private boolean isPasswordModified() {
    return mySettingsPane.isPasswordModified();
  }

  public void apply() throws ConfigurationException {
    if (mySettingsPane != null) {
      mySettings.setLogin(mySettingsPane.getLogin());
      if (isPasswordModified()) {
        mySettings.setPassword(mySettingsPane.getPassword());
        mySettingsPane.resetPasswordModification();
      }
      mySettings.setHost(mySettingsPane.getHost());
    }
  }

  public void reset() {
    if (mySettingsPane != null) {
      String login = mySettings.getLogin();
      mySettingsPane.setLogin(login);
      mySettingsPane.setPassword(StringUtil.isEmptyOrSpaces(login) ? "" : DEFAULT_PASSWORD_TEXT);
      mySettingsPane.resetPasswordModification();
      mySettingsPane.setHost(mySettings.getHost());
    }
  }

  public void disposeUIResources() {
    mySettingsPane = null;
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  @Nullable
  @Override
  public Configurable getConfigurable(Project project) {
    return this;
  }
}
