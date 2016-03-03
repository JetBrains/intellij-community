package com.intellij.remoteServer;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurator;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerConnector;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.debug.DebugConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;

/**
 * @author nik
 */
public abstract class ServerType<C extends ServerConfiguration> {
  public static final ExtensionPointName<ServerType> EP_NAME = ExtensionPointName.create("com.intellij.remoteServer.type");
  private final String myId;

  protected ServerType(String id) {
    myId = id;
  }

  public final String getId() {
    return myId;
  }

  @NotNull
  public abstract String getPresentableName();

  @NotNull
  public abstract Icon getIcon();

  @NotNull
  public abstract C createDefaultConfiguration();

  @NotNull
  public RemoteServerConfigurable createServerConfigurable(@NotNull C configuration) {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated override {@link #createServerConfigurable(com.intellij.remoteServer.configuration.ServerConfiguration)} instead
   */
  @NotNull
  public UnnamedConfigurable createConfigurable(@NotNull C configuration) {
    return createServerConfigurable(configuration);
  }

  @NotNull
  public abstract DeploymentConfigurator<?, C> createDeploymentConfigurator(Project project);

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
    return new Comparator<Deployment>() {

      @Override
      public int compare(Deployment o1, Deployment o2) {
        return o1.getName().compareTo(o2.getName());
      }
    };
  }

  @Nullable
  public String getCustomToolWindowId() {
    return null;
  }
}
