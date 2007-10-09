package com.intellij.lang.ant.config.impl;

import com.intellij.execution.StepsBeforeRunProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
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

  public boolean hasTask(RunConfiguration configuration) {
    final AntConfiguration config = AntConfiguration.getInstance(myProject);
    int attemptCount = 0; // need this in order to make sure we will not block swing thread forever
    while (!config.isInitialized() && attemptCount < 6000) {
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ignored) {
      }
      attemptCount++;
    }
    return config.hasTasksToExecuteBeforeRun(configuration);
  }

  public boolean executeTask(DataContext context, RunConfiguration configuration) {
    return AntConfiguration.getInstance(myProject).executeTaskBeforeRun(context, configuration);
  }

  public void copyTaskData(RunConfiguration from, RunConfiguration to) {
    AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
    final AntBuildTarget antBuildTarget = antConfiguration.getTargetForBeforeRunEvent(from.getType(), from.getName());

    if (antBuildTarget != null){
      antConfiguration.setTargetForBeforeRunEvent(antBuildTarget.getModel().getBuildFile(), antBuildTarget.getName(), to.getType(), to.getName());
    }
  }
}
