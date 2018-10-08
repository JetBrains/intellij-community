// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.griffon;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.groovy.mvc.MvcRunConfiguration;

public class GriffonRunConfiguration extends MvcRunConfiguration {

  public GriffonRunConfiguration(ConfigurationFactory factory, Project project, String name, String cmdLine) {
    super(name, new RunConfigurationModule(project), factory, GriffonFramework.getInstance());
    this.cmdLine = cmdLine;
  }

  @Override
  protected String getNoSdkMessage() {
    return "Griffon SDK is not configured";
  }
}
