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
  private GithubSettingsPanel mySettingsPane;
  private final GithubSettings mySettings;

  public GitHubSettingsConfigurable() {
    mySettings = GithubSettings.getInstance();
  }

  @NotNull
  public String getDisplayName() {
    return "GitHub";
  }

  @NotNull
  public String getHelpTopic() {
    return "settings.github";
  }

  @NotNull
  public JComponent createComponent() {
    if (mySettingsPane == null) {
      mySettingsPane = new GithubSettingsPanel(mySettings);
    }
    return mySettingsPane.getPanel();
  }

  public boolean isModified() {
    return mySettingsPane != null && (!Comparing.equal(mySettings.getHost(), mySettingsPane.getHost()) ||
                                      !Comparing.equal(mySettings.getLogin(), mySettingsPane.getLogin()) ||
                                      isPasswordModified());
  }

  private boolean isPasswordModified() {
    return mySettingsPane.isPasswordModified();
  }

  public void apply() throws ConfigurationException {
    if (mySettingsPane != null) {
      if (isPasswordModified()) {
        mySettings.setAuthData(mySettingsPane.getAuthData(), true);
        mySettingsPane.resetPasswordModification();
      }
      else {
        mySettings.setHost(mySettingsPane.getHost());
        mySettings.setLogin(mySettingsPane.getLogin());
      }
    }
  }

  public void reset() {
    if (mySettingsPane != null) {
      mySettingsPane.reset();
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
