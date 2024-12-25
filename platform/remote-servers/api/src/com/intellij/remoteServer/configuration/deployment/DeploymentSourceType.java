// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Implement this class to provide a custom type of deployment source which can be used in 'Deploy to Server' run configurations.
 * <p>
 * The implementation should be registered in {@code plugin.xml} file:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;remoteServer.deploymentSource.type implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * </p>
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

  public abstract @NotNull S load(@NotNull Element tag, @NotNull Project project);

  public abstract void save(@NotNull S s, @NotNull Element tag);


  public void setBuildBeforeRunTask(@NotNull RunConfiguration configuration, @NotNull S source) {
  }

  public void updateBuildBeforeRunOption(@NotNull JComponent runConfigurationEditorComponent, @NotNull Project project, @NotNull S source, boolean select) {
  }

  public boolean isEditableInDumbMode() {
    return false;
  }
}
