/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.configuration;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    Application application = ApplicationManager.getApplication();
    List<ServerType<?>> excludedTypes = Arrays.stream(application.getExtensions(Configurable.APPLICATION_CONFIGURABLE))
      .filter(RemoteServerListConfigurableProvider::isRemoteServerListConfigurable)
      .filter(ConfigurableEP::canCreateConfigurable)
      .map(ConfigurableEP::createConfigurable)
      .map(RemoteServerListConfigurable.class::cast)
      .map(RemoteServerListConfigurable::getDisplayedServerTypes)
      .flatMap(List::stream)
      .collect(Collectors.toList());

    ServerType<?>[] allServerTypes = ServerType.EP_NAME.getExtensions();
    return ContainerUtil.filter(allServerTypes, t -> !excludedTypes.contains(t));
  }

  private static boolean isRemoteServerListConfigurable(@NotNull ConfigurableEP<Configurable> ep) {
    Class<?> type = ep.getConfigurableType();
    return type != null && RemoteServerListConfigurable.class.isAssignableFrom(type);
  }
}
