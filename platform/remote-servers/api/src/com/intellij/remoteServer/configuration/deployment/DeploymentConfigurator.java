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

import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public abstract class DeploymentConfigurator<D extends DeploymentConfiguration, S extends ServerConfiguration> {
  @NotNull
  public abstract List<DeploymentSource> getAvailableDeploymentSources();

  @NotNull
  public abstract D createDefaultConfiguration(@NotNull DeploymentSource source);

  @Nullable
  public abstract SettingsEditor<D> createEditor(@NotNull DeploymentSource source, @NotNull RemoteServer<S> server);

  /**
   * @see LocatableConfiguration#isGeneratedName()
   */
  public boolean isGeneratedConfigurationName(@NotNull String name,
                                              @NotNull DeploymentSource deploymentSource,
                                              @NotNull D deploymentConfiguration) {
    return isGeneratedConfigurationName(name, deploymentSource);
  }

  /**
   * @see LocatableConfiguration#isGeneratedName()
   * @deprecated Since 2017.3, subclasses should directly override {@link
   * #isGeneratedConfigurationName(String, DeploymentSource, DeploymentConfiguration)}
   */
  @Deprecated
  protected boolean isGeneratedConfigurationName(@NotNull String name, @NotNull DeploymentSource deploymentSource) {
    return false;
  }

  /**
   * @see LocatableConfiguration#suggestedName()
   */
  @Nullable
  public String suggestConfigurationName(@NotNull DeploymentSource deploymentSource, @NotNull D deploymentConfiguration) {
    return suggestConfigurationName(deploymentSource);
  }

  /**
   * @see LocatableConfiguration#suggestedName()
   * @deprecated Since 2017.3, subclasses should directly override {@link
   * {@link #suggestConfigurationName(DeploymentSource, DeploymentConfiguration)}
   */
  @Deprecated
  @Nullable
  protected String suggestConfigurationName(@NotNull DeploymentSource deploymentSource) {
    return null;
  }
}
