// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServerListener;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.util.CloudConfigurationBase;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@State(name = "RemoteServers",
  category = SettingsCategory.TOOLS,
  exportable = true,
  storages = @Storage(value = "remote-servers.xml", roamingType = RoamingType.DISABLED))
public final class RemoteServersManagerImpl extends RemoteServersManager implements PersistentStateComponent<RemoteServersManagerState> {
  private SkipDefaultValuesSerializationFilters myDefaultValuesFilter = new SkipDefaultValuesSerializationFilters();
  private final List<RemoteServer<?>> myServers = new CopyOnWriteArrayList<>();
  private final List<RemoteServerState> myUnknownServers = new ArrayList<>();
  private final Map<RemoteServer<?>, UUID> myThirdPartyServerIds = CollectionFactory.createWeakIdentityMap(2, .75f);

  public RemoteServersManagerImpl() {
    ServerType.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull ServerType addedType, @NotNull PluginDescriptor pluginDescriptor) {
        List<RemoteServerState> nowKnownStates = ContainerUtil.filter(myUnknownServers, next -> addedType.getId().equals(next.myTypeId));
        nowKnownStates.forEach(nextState -> {
          myUnknownServers.remove(nextState);
          addServer(createConfiguration(addedType, nextState));
        });
      }

      @Override
      public void extensionRemoved(@NotNull ServerType removedType, @NotNull PluginDescriptor pluginDescriptor) {
        @SuppressWarnings("unchecked") List<RemoteServer<?>> removedServers = getServers(removedType);
        removedServers.forEach(nextServer -> {
          RemoteServerState nextState = createServerState(nextServer);
          removeServer(nextServer);
          myUnknownServers.add(nextState);
        });
        if (!removedServers.isEmpty()) {
          myDefaultValuesFilter = new SkipDefaultValuesSerializationFilters();
        }
      }
    }, null);
  }

  @Override
  public List<RemoteServer<?>> getServers() {
    return Collections.unmodifiableList(myServers);
  }

  @Override
  public <C extends ServerConfiguration> List<RemoteServer<C>> getServers(@NotNull ServerType<C> type) {
    List<RemoteServer<C>> servers = new ArrayList<>();
    for (RemoteServer<?> server : myServers) {
      if (server.getType().equals(type)) {
        //noinspection unchecked
        servers.add((RemoteServer<C>)server);
      }
    }
    return servers;
  }

  @Override
  public @Nullable <C extends ServerConfiguration> RemoteServer<C> findByName(@NotNull String name, @NotNull ServerType<C> type) {
    for (RemoteServer<?> server : myServers) {
      if (server.getType().equals(type) && server.getName().equals(name)) {
        //noinspection unchecked
        return (RemoteServer<C>)server;
      }
    }
    return null;
  }

  @Override
  public @NotNull UUID getId(RemoteServer<?> server) {
    if (server instanceof RemoteServerImpl<?> impl) {
      return impl.getUniqueId();
    }
    synchronized (myThirdPartyServerIds) {
      return myThirdPartyServerIds.computeIfAbsent(server, s -> UUID.randomUUID());
    }
  }

  @Override
  public @Nullable <C extends ServerConfiguration> RemoteServer<C> findById(@NotNull UUID id) {
    for (RemoteServer<?> server : myServers) {
      if (id.equals(getId(server))) {
        //noinspection unchecked
        return (RemoteServer<C>)server;
      }
    }
    return null;
  }

  @Override
  public @NotNull <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type, @NotNull String name) {
    return new RemoteServerImpl<>(name, type, type.createDefaultConfiguration());
  }

  @Override
  public @NotNull <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type) {
    String name = UniqueNameGenerator.generateUniqueName(
      type.getPresentableName(), s -> getServers(type).stream().map(RemoteServer::getName).noneMatch(s::equals));
    return createServer(type, name);
  }

  @Override
  public void addServer(RemoteServer<?> server) {
    myServers.add(server);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(RemoteServerListener.TOPIC).serverAdded(server);
  }

  @Override
  public void removeServer(RemoteServer<?> server) {
    myServers.remove(server);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(RemoteServerListener.TOPIC).serverRemoved(server);
  }

  @Override
  public @NotNull RemoteServersManagerState getState() {
    RemoteServersManagerState state = new RemoteServersManagerState();
    for (RemoteServer<?> server : myServers) {
      state.myServers.add(createServerState(server));
    }
    state.myServers.addAll(myUnknownServers);
    return state;
  }

  @Override
  public void loadState(@NotNull RemoteServersManagerState state) {
    myUnknownServers.clear();
    myServers.clear();

    List<CloudConfigurationBase<?>> needsMigration = new LinkedList<>();
    for (RemoteServerState server : state.myServers) {
      ServerType<?> type = findServerType(server.myTypeId);
      if (type == null) {
        myUnknownServers.add(server);
      }
      else {
        RemoteServer<? extends ServerConfiguration> nextServer = createConfiguration(type, server);
        myServers.add(nextServer);
        ServerConfiguration nextConfig = nextServer.getConfiguration();
        if (nextConfig instanceof CloudConfigurationBase && ((CloudConfigurationBase<?>)nextConfig).shouldMigrateToPasswordSafe()) {
          needsMigration.add((CloudConfigurationBase<?>)nextConfig);
        }
      }
    }

    if (!needsMigration.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        for (CloudConfigurationBase nextConfig : needsMigration) {
          nextConfig.migrateToPasswordSafe();
        }
      });
    }
  }

  private @NotNull RemoteServerState createServerState(@NotNull RemoteServer<?> server) {
    RemoteServerState serverState = new RemoteServerState();
    serverState.myName = server.getName();
    serverState.myTypeId = server.getType().getId();
    serverState.myConfiguration = XmlSerializer.serialize(server.getConfiguration().getSerializer().getState(), myDefaultValuesFilter);
    return serverState;
  }

  private static @NotNull <C extends ServerConfiguration> RemoteServerImpl<C> createConfiguration(ServerType<C> type, RemoteServerState server) {
    C configuration = type.createDefaultConfiguration();
    PersistentStateComponent<?> serializer = configuration.getSerializer();
    ComponentSerializationUtil.loadComponentState(serializer, server.myConfiguration);
    return new RemoteServerImpl<>(server.myName, type, configuration);
  }

  private static @Nullable ServerType<?> findServerType(@NotNull String typeId) {
    return ServerType.EP_NAME.findFirstSafe(next -> typeId.equals(next.getId()));
  }
}
