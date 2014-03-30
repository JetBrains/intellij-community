/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class DeploymentSourceType<S extends DeploymentSource> {
  public static final ExtensionPointName<DeploymentSourceType<?>> EP_NAME = ExtensionPointName.create("com.intellij.remoteServer.deploymentSource.type");
  private final String myId;

  protected DeploymentSourceType(@NotNull String id) {
    myId = id;
  }

  public final String getId() {
    return myId;
  }

  @NotNull
  public abstract S load(@NotNull Element tag, @NotNull Project project);

  public abstract void save(@NotNull S s, @NotNull Element tag);


  public void setBuildBeforeRunTask(@NotNull RunConfiguration configuration, @NotNull S source) {
  }

  public void updateBuildBeforeRunOption(@NotNull JComponent runConfigurationEditorComponent, @NotNull Project project, @NotNull S source, boolean select) {
  }
}
