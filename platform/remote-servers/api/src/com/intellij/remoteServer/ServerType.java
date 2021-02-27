// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.configuration.deployment.SingletonDeploymentSourceType;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.debug.DebugConnector;
import com.intellij.util.DeprecatedMethodException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class ServerType<C extends ServerConfiguration> {
  public static final ExtensionPointName<ServerType<?>> EP_NAME = ExtensionPointName.create("com.intellij.remoteServer.type");
  private final String myId;

  protected ServerType(String id) {
    myId = id;
  }

  public final String getId() {
    return myId;
  }

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  public abstract String getPresentableName();

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getDeploymentConfigurationTypePresentableName() {
    return CloudBundle.message("server.type.deployment.configuration.typ.presentable.name.0.deployment", getPresentableName());
  }

  /**
   * This method must be overridden and a proper ID must be returned from it (it'll be used as a key in run configuration file).
   */
  @NotNull @NonNls
  public String getDeploymentConfigurationFactoryId() {
    DeprecatedMethodException.reportDefaultImplementation(getClass(), "getDeploymentConfigurationFactoryId",
      "The default implementation delegates to 'getDeploymentConfigurationTypePresentableName' which is supposed to be localized but return value of this method must not be localized.");
    return getDeploymentConfigurationTypePresentableName();
  }

  @NotNull
  public String getHelpTopic() {
    return "reference.settings.clouds";
  }

  @NotNull
  public abstract Icon getIcon();

  /**
   * Returns whether the instance returned from {@link #createDefaultConfiguration()} has <em>reasonably good</em> chances to work correctly.
   * The auto-detected instance is <em>not</em> required to work perfectly, connection to it will be tested, and the instance will
   * be persisted only if the test is successful.
   * <p/>
   * The capability to auto-detect configurations will unlock UI elements which normally requires user to manually configure the server.
   * E.g deployments for auto-detecting server types will be always shown in the 'New' popup in 'Edit Configurations' dialog.
   */
  public boolean canAutoDetectConfiguration() {
    return false;
  }

  @NotNull
  public abstract C createDefaultConfiguration();

  @NotNull
  public RemoteServerConfigurable createServerConfigurable(@NotNull C configuration) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public abstract DeploymentConfigurator<?, C> createDeploymentConfigurator(Project project);

  /**
   * Returns list of the singleton deployment sources types available in addition to the project-dependent deployment sources
   * enumerated via {@link DeploymentConfigurator#getAvailableDeploymentSources()}.
   */
  public List<SingletonDeploymentSourceType> getSingletonDeploymentSourceTypes() {
    return Collections.emptyList();
  }

  /**
   * @return <code>false</code>, if all supported deployment sources are of {@link SingletonDeploymentSourceType} type, so
   * {@link DeploymentConfigurator#getAvailableDeploymentSources()} <strong>now is and always will be</strong> empty.
   */
  public boolean mayHaveProjectSpecificDeploymentSources() {
    return true;
  }

  @NotNull
  public abstract ServerConnector<?> createConnector(@NotNull C configuration, @NotNull ServerTaskExecutor asyncTasksExecutor);

  @NotNull
  public ServerConnector<?> createConnector(@NotNull RemoteServer<C> server, @NotNull ServerTaskExecutor asyncTasksExecutor) {
    return createConnector(server.getConfiguration(), asyncTasksExecutor);
  }

  /**
   * @return a non-null instance of {@link DebugConnector} if the server supports deployment in debug mode
   */
  @Nullable
  public DebugConnector<?, ?> createDebugConnector() {
    return null;
  }

  @NotNull
  public Comparator<Deployment> getDeploymentComparator() {
    return Comparator.comparing(Deployment::getName);
  }

  @Nullable
  public String getCustomToolWindowId() {
    return null;
  }
}
