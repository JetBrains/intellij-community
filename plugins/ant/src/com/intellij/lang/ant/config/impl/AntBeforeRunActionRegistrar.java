/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 24-Jul-2006
 * Time: 14:46:05
 */
package com.intellij.lang.ant.config.impl;

import com.intellij.execution.RunManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AntBeforeRunActionRegistrar implements ProjectComponent {


  public AntBeforeRunActionRegistrar(AntConfigurationImpl antConfiguration, RunManager runManager, Project project) {
    antConfiguration.registerAntTargetBeforeRun(runManager, project);
  }

  public void projectOpened() {

  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "com.intellij.lang.ant.config.impl.AntBeforeRunActionRegistrar";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}