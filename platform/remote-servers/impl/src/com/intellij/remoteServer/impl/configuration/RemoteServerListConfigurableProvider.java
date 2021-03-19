// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides default configurable for server configurations of different
 * {@link ServerType}s.
 * <p>
 * {@link ServerType} is included into the configurable (f.e. if it has not a
 * specific configurable) by declaring {@link IncludeServerType} extension with
 * the corresponding {@code serverType} attribute.
 */
public final class RemoteServerListConfigurableProvider extends ConfigurableProvider {
  @Override
  public boolean canCreateConfigurable() {
    List<ServerType<?>> serverTypes = getServerTypesIncludedInList();
    return !serverTypes.isEmpty();
  }

  @Override
  public Configurable createConfigurable() {
    return new RemoteServerListConfigurable(RemoteServersManager.getInstance(), getServerTypesIncludedInList(), null);
  }

  @NotNull
  private static List<ServerType<?>> getServerTypesIncludedInList() {
    Set<String> includedTypes = IncludeServerType.EP_NAME.extensions().map(type -> type.myServerType).collect(Collectors.toSet());
    return ContainerUtil.filter(ServerType.EP_NAME.getExtensionList(), type -> includedTypes.contains(type.getClass().getName()));
  }

  /**
   * Includes server configurations of specific {@link ServerType} to default
   * configurable.
   */
  public static class IncludeServerType {
    public static final ExtensionPointName<IncludeServerType> EP_NAME =
      ExtensionPointName.create("com.intellij.remoteServer.defaultConfigurable.includeServerType");

    @Attribute("serverType")
    public String myServerType;
  }
}
