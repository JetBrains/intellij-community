package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
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
    return mySettingsPane == null || !StringUtil.equals(mySettings.getLogin(), mySettingsPane.getLogin()) ||
           !StringUtil.equals(mySettings.getPassword(), mySettingsPane.getPassword());
  }

  public void apply() throws ConfigurationException {
    if (mySettingsPane != null) {
      mySettings.setLogin(mySettingsPane.getLogin());
      mySettings.setPassword(mySettingsPane.getPassword());
    }
  }

  public void reset() {
    if (mySettingsPane != null) {
      mySettingsPane.setLogin(mySettings.getLogin());
      mySettingsPane.setPassword(mySettings.getPassword());
    }
  }

  public void disposeUIResources() {
    mySettingsPane = null;
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }
}
