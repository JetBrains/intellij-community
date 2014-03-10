package com.intellij.remote;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author lene
 *         Date: 05.07.13
 */
public interface SshConnectionProvider {
  ExtensionPointName<SshConnectionProvider> EP_NAME = ExtensionPointName.create("RemoteRun.sshConnectionProvider");

  @NotNull
  Collection<? extends RemoteConnector> collectRemoteConnectors();

  @Nullable
  RemoteConnector getRemoteConnector(RemoteConnectionType type, @Nullable String id, Project project, Module module);

  @Nullable
  String getSettingCreationDescription();

  @NotNull
  @Nls
  String getRadioButtonDescription();

  @NotNull
  RemoteConnectionType getTypeForConfigurable();
}
