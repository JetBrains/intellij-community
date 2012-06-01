package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubSettings;

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

  @NotNull
  public String getHelpTopic() {
    return "settings.github";
  }

  public JComponent createComponent() {
    if (mySettingsPane == null) {
      mySettingsPane = new GithubSettingsPanel(mySettings);
    }
    reset();
    return mySettingsPane.getPanel();
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
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
