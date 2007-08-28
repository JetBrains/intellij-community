package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;

import javax.swing.*;

public class JUnitConfigurationType implements LocatableConfigurationType {
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/junit.png");
  private final ConfigurationFactory myFactory;

  /**reflection*/
  public JUnitConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new JUnitConfiguration("", project, this);
      }

    };
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return ExecutionBundle.message("junit.configuration.display.name");
  }

  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("junit.configuration.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(final Location location) {
    return null;
  }

  public boolean isConfigurationByElement(final RunConfiguration configuration, final Project project, final PsiElement element) {
    final JUnitConfiguration unitConfiguration = (JUnitConfiguration)configuration;
    final TestObject testObject = unitConfiguration.getTestObject();
    return testObject != null && testObject.isConfiguredByElement(unitConfiguration, element);
  }

  public String getComponentName() {
    return "JUnit";
  }

  public static JUnitConfigurationType getInstance() {
    return ApplicationManager.getApplication().getComponent(JUnitConfigurationType.class);
  }
}