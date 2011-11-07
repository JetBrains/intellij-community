package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubSettings;
import org.jetbrains.plugins.github.GithubUtil;

import javax.swing.*;

/**
 * @author oleg
 */
public class GitHubSettingsConfigurable implements SearchableConfigurable {
  private GithubSettingsPanel mySettingsPane;
  private final GithubSettings mySettings;

  public GitHubSettingsConfigurable() {
    mySettings = GithubSettings.getInstance();
  }

  public String getDisplayName() {
    return "GitHub";
  }

  public Icon getIcon() {
    return GithubUtil.GITHUB_ICON;
  }

  public String getHelpTopic() {
    return "settings.github";
  }

  public JComponent createComponent() {
    if (mySettingsPane == null) {
      mySettingsPane = new GithubSettingsPanel();
    }
    reset();
    return mySettingsPane.getPanel();
  }

  public boolean isModified() {
    return mySettingsPane != null && (!Comparing.equal(mySettings.getLogin(), mySettingsPane.getLogin()) ||
           !Comparing.strEqual(mySettings.getPassword(), mySettingsPane.getPassword()) ||
           !Comparing.equal(mySettings.getHost(), mySettingsPane.getHost()));
  }

  public void apply() throws ConfigurationException {
    if (mySettingsPane != null) {
      mySettings.setLogin(mySettingsPane.getLogin());
      mySettings.setPassword(mySettingsPane.getPassword());
      mySettings.setHost(mySettingsPane.getHost());
    }
  }

  public void reset() {
    if (mySettingsPane != null) {
      mySettingsPane.setLogin(mySettings.getLogin());
      mySettingsPane.setPassword(mySettings.getPassword());
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
}
