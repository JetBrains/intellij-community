// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class RemoteServerImpl<C extends ServerConfiguration> implements RemoteServer<C> {
  private String myName;
  private final ServerType<C> myType;
  private final C myConfiguration;
  private final UUID myId;

  public RemoteServerImpl(String name, ServerType<C> type, C configuration) {
    myName = name;
    myType = type;
    myConfiguration = configuration;
    myId = UUID.randomUUID();
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull ServerType<C> getType() {
    return myType;
  }

  @Override
  public @NotNull C getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void setName(String name) {
    myName = name;
  }

  @Override
  public @NotNull UUID getUniqueId() {
    return myId;
  }
}
