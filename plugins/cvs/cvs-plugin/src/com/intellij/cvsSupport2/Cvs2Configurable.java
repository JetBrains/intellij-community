package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationPanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;

import javax.swing.*;

import org.jetbrains.annotations.NonNls;

class Cvs2Configurable implements Configurable {
  private CvsConfigurationPanel  myComponent = null;
  private final Project myProject;

  public Cvs2Configurable(Project project) {
    myProject = project;
  }

  @NonNls public String getDisplayName() {
    return "CVS2";
  }

  public Icon getIcon() {
    
    return null;
  }

  public String getHelpTopic() {
    return "project.propCVS";
  }

  public JComponent createComponent() {
    myComponent = new CvsConfigurationPanel(myProject);
    myComponent.updateFrom(getCvsConfiguration(), getAppLevelConfiguration());
    return myComponent.getPanel();
  }

  private CvsApplicationLevelConfiguration getAppLevelConfiguration() {
    return CvsApplicationLevelConfiguration.getInstance();
  }

  private CvsConfiguration getCvsConfiguration() {
    return CvsConfiguration.getInstance(myProject);
  }

  public boolean isModified() {
    return !myComponent.equalsTo(getCvsConfiguration(), getAppLevelConfiguration());
  }

  public void apply() throws ConfigurationException {
    myComponent.saveTo(getCvsConfiguration(), getAppLevelConfiguration());
  }

  public void reset() {
    myComponent.updateFrom(getCvsConfiguration(), getAppLevelConfiguration());
  }

  public void disposeUIResources() {
    myComponent = null;
  }
}
