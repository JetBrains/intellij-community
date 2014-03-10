package com.intellij.remote;

import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.remote.RemoteConnectionType;
import com.intellij.remote.RemoteConnector;
import com.intellij.remote.SshConnectionProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class VagrantSshConnectionProvider implements SshConnectionProvider {

  @NotNull
  @Override
  public Collection<? extends RemoteConnector> collectRemoteConnectors() {
    return collectVagrantConnections();
  }

  private static List<RemoteConnector> collectVagrantConnections() {
    List<RemoteConnector> result = Lists.newArrayList();
    VagrantSupport vs = VagrantSupport.getInstance();
    if (vs != null) {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        result.addAll(vs.getVagrantInstancesConnectors(project));
      }
    }

    return result;
  }


  @Nullable
  @Override
  public RemoteConnector getRemoteConnector(RemoteConnectionType type, @Nullable String id, Project project, Module module) {
    for (RemoteConnector connector : collectRemoteConnectors()) {
      if (connector.getType() == type && (connector.getId() != null && connector.getId().equals(id))) {
        return connector;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getSettingCreationDescription() {
    return "add Vagrant";
  }

  @Nls
  @NotNull
  @Override
  public String getRadioButtonDescription() {
    return "Current Vagrant";
  }

  @NotNull
  @Override
  public RemoteConnectionType getTypeForConfigurable() {
    return RemoteConnectionType.CURRENT_VAGRANT;
  }
}
