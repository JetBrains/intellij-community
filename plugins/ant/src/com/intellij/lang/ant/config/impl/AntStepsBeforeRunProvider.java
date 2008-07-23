package com.intellij.lang.ant.config.impl;

import com.intellij.execution.StepsBeforeRunProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

/**
 * @author Vladislav.Kaznacheev
 */
public class AntStepsBeforeRunProvider implements StepsBeforeRunProvider {

  private final Project myProject;

  public AntStepsBeforeRunProvider(Project project) {
    myProject = project;
  }

  public String getStepName() {
    return AntConfiguration.ANT;
  }

  public String getStepDescription(final RunConfiguration runConfiguration) {
    final AntConfigurationImpl antConfiguration = (AntConfigurationImpl)AntConfiguration.getInstance(myProject);
    final ExecuteBeforeRunEvent event = antConfiguration.findExecuteBeforeRunEvent(runConfiguration);
    final AntBuildTarget buildTarget = antConfiguration.getTargetForEvent(event);
    return buildTarget != null ? getPresentableDescription(buildTarget.getName()) : "";
  }

  public boolean hasTask(RunConfiguration configuration) {
    final AntConfiguration config = AntConfiguration.getInstance(myProject);
    ((AntConfigurationBase)config).ensureInitialized();
    return config.hasTasksToExecuteBeforeRun(configuration);
  }

  public boolean executeTask(DataContext context, RunConfiguration configuration) {
    return AntConfiguration.getInstance(myProject).executeTaskBeforeRun(context, configuration);
  }

  public void copyTaskData(RunConfiguration from, RunConfiguration to) {
    AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
    final AntBuildTarget antBuildTarget = antConfiguration.getTargetForBeforeRunEvent(from);

    if (antBuildTarget != null) {
      antConfiguration
          .setTargetForBeforeRunEvent(antBuildTarget.getModel().getBuildFile(), antBuildTarget.getName(), to);
    }
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  public boolean hasConfigurationButton() {
    return true;
  }

  public String configureStep(final RunConfiguration runConfiguration) {
    final AntConfigurationImpl antConfiguration = (AntConfigurationImpl)AntConfiguration.getInstance(myProject);
    ExecuteBeforeRunEvent event = antConfiguration.findExecuteBeforeRunEvent(runConfiguration);
    AntBuildTarget buildTarget = antConfiguration.getTargetForEvent(event);
    final TargetChooserDialog dlg = new TargetChooserDialog(myProject, buildTarget, antConfiguration);
    dlg.show();
    if (dlg.isOK()) {
      buildTarget = dlg.getSelectedTarget();
      if (event == null) {
        event = new ExecuteBeforeRunEvent(runConfiguration);
      }
      if (buildTarget != null) {
        antConfiguration.setTargetForEvent(buildTarget.getModel().getBuildFile(), buildTarget.getName(), event);
      }
      else {
        antConfiguration.clearTargetForEvent(event);
      }
    }
    final String targetName = buildTarget != null ? buildTarget.getName() : null;
    return getPresentableDescription(targetName);
  }

  private static String getPresentableDescription(final String targetName) {
    return targetName != null ? "\'" + targetName + "\'" : "";
  }
}
