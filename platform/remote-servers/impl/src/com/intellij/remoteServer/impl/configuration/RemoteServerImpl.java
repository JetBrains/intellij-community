// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import org.jetbrains.annotations.NotNull;

public class RemoteServerImpl<C extends ServerConfiguration> implements RemoteServer<C> {
  private String myName;
  private final ServerType<C> myType;
  private final C myConfiguration;

  public RemoteServerImpl(String name, ServerType<C> type, C configuration) {
    myName = name;
    myType = type;
    myConfiguration = configuration;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public ServerType<C> getType() {
    return myType;
  }

  @NotNull
  @Override
  public C getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void setName(String name) {
    myName = name;
  }
}
