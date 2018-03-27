// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServerListener;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
@State(name = "RemoteServers", storages = @Storage("remote-servers.xml"))
public class RemoteServersManagerImpl extends RemoteServersManager implements PersistentStateComponent<RemoteServersManagerState> {
  public static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  private List<RemoteServer<?>> myServers = new ArrayList<>();
  private List<RemoteServerState> myUnknownServers = new ArrayList<>();
  private final MessageBus myMessageBus;

  public RemoteServersManagerImpl(MessageBus messageBus) {
    myMessageBus = messageBus;
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
        servers.add((RemoteServer<C>)server);
      }
    }
    return servers;
  }

  @Nullable
  @Override
  public <C extends ServerConfiguration> RemoteServer<C> findByName(@NotNull String name, @NotNull ServerType<C> type) {
    for (RemoteServer<?> server : myServers) {
      if (server.getType().equals(type) && server.getName().equals(name)) {
        return (RemoteServer<C>)server;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type, @NotNull String name) {
    return new RemoteServerImpl<>(name, type, type.createDefaultConfiguration());
  }

  @Override
  @NotNull
  public <C extends ServerConfiguration> RemoteServer<C> createServer(@NotNull ServerType<C> type) {
    String name = UniqueNameGenerator.generateUniqueName(
      type.getPresentableName(), s -> getServers(type).stream().map(RemoteServer::getName).noneMatch(s::equals));
    return createServer(type, name);
  }

  @Override
  public void addServer(RemoteServer<?> server) {
    myServers.add(server);
    myMessageBus.syncPublisher(RemoteServerListener.TOPIC).serverAdded(server);
  }

  @Override
  public void removeServer(RemoteServer<?> server) {
    myServers.remove(server);
    myMessageBus.syncPublisher(RemoteServerListener.TOPIC).serverRemoved(server);
  }

  @Nullable
  @Override
  public RemoteServersManagerState getState() {
    RemoteServersManagerState state = new RemoteServersManagerState();
    for (RemoteServer<?> server : myServers) {
      RemoteServerState serverState = new RemoteServerState();
      serverState.myName = server.getName();
      serverState.myTypeId = server.getType().getId();
      serverState.myConfiguration = XmlSerializer.serialize(server.getConfiguration().getSerializer().getState(), SERIALIZATION_FILTERS);
      state.myServers.add(serverState);
    }
    state.myServers.addAll(myUnknownServers);
    return state;
  }

  @Override
  public void loadState(RemoteServersManagerState state) {
    myUnknownServers.clear();
    myServers.clear();
    for (RemoteServerState server : state.myServers) {
      ServerType<?> type = findServerType(server.myTypeId);
      if (type == null) {
        myUnknownServers.add(server);
      }
      else {
        myServers.add(createConfiguration(type, server));
      }
    }
  }

  private static <C extends ServerConfiguration> RemoteServerImpl<C> createConfiguration(ServerType<C> type, RemoteServerState server) {
    C configuration = type.createDefaultConfiguration();
    PersistentStateComponent<?> serializer = configuration.getSerializer();
    ComponentSerializationUtil.loadComponentState(serializer, server.myConfiguration);
    return new RemoteServerImpl<>(server.myName, type, configuration);
  }

  @Nullable
  private static ServerType<?> findServerType(@NotNull String typeId) {
    for (ServerType serverType : ServerType.EP_NAME.getExtensions()) {
      if (serverType.getId().equals(typeId)) {
        return serverType;
      }
    }
    return null;
  }
}
