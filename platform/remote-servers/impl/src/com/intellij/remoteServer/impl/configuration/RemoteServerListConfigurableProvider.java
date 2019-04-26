// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Sergey.Malenkov
 */
public final class RemoteServerListConfigurableProvider extends ConfigurableProvider {
  @Override
  public boolean canCreateConfigurable() {
    List<ServerType<?>> serverTypes = getServerTypesInCloudsList();
    return !serverTypes.isEmpty();
  }

  @Override
  public Configurable createConfigurable() {
    return new RemoteServerListConfigurable(RemoteServersManager.getInstance(), getServerTypesInCloudsList(), null);
  }

  @NotNull
  private static List<ServerType<?>> getServerTypesInCloudsList() {
    Set<ServerType<?>> excludedTypes = Configurable.APPLICATION_CONFIGURABLE.extensions()
      .flatMap(RemoteServerListConfigurableProvider::tryGetServerTypes)
      .collect(Collectors.toSet());

    List<ServerType> collection = ServerType.EP_NAME.getExtensionList();
    if (collection.isEmpty()) {
      return Collections.emptyList();
    }
    final List<ServerType<?>> result = new SmartList<>();
    for (ServerType<?> t : collection) {
      if (!excludedTypes.contains(t)) {
        result.add(t);
      }
    }
    return result;
  }

  @NotNull
  private static Stream<ServerType<?>> tryGetServerTypes(@NotNull ConfigurableEP<?> ep) {
    Class<?> type = ep.getConfigurableType();
    if (type != null && RemoteServerListConfigurable.class.isAssignableFrom(type)) {
      UnnamedConfigurable configurable = ep.createConfigurable();
      if (configurable instanceof RemoteServerListConfigurable) {
        return ((RemoteServerListConfigurable)configurable).getDisplayedServerTypes().stream();
      }
    }
    return Stream.empty();
  }
}
