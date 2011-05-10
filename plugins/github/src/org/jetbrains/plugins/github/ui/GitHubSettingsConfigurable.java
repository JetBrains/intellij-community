package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubSettings;

import javax.swing.*;

/**
 * @author oleg
 */
public class GitHubSettingsConfigurable implements SearchableConfigurable {
  public static final Icon ICON = IconLoader.getIcon("/icons/github.png");
  private GithubSettingsPanel mySettingsPane;
  private final GithubSettings mySettings;

  public GitHubSettingsConfigurable() {
    mySettings = GithubSettings.getInstance();
  }

  public String getDisplayName() {
    return "GitHub";
  }

  public Icon getIcon() {
    return ICON;
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
    return mySettingsPane == null || !mySettings.getLogin().equals(mySettingsPane.getLogin()) ||
           (mySettings.getPassword() != null && !mySettings.getPassword().equals(mySettingsPane.getPassword())) ||
           !mySettings.getHost().equals(mySettingsPane.getHost());
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
