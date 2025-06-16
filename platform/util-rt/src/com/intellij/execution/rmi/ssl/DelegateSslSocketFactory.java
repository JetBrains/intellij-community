// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;

@ApiStatus.Internal
public abstract class DelegateSslSocketFactory extends SSLSocketFactory {
  private final SSLSocketFactory myFactory;

  DelegateSslSocketFactory(SSLSocketFactory factory) throws GeneralSecurityException {
    super();
    myFactory = factory;
  }

  @Override
  @NotNull
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return myFactory.createSocket(host, port);
  }

  @Override
  @NotNull
  public Socket createSocket(String host, int port) throws IOException {
    return myFactory.createSocket(host, port);
  }

  @Override
  @NotNull
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
    return myFactory.createSocket(host, port, localHost, localPort);
  }

  @Override
  @NotNull
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    return myFactory.createSocket(address, port, localAddress, localPort);
  }

  @Override
  public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
    return myFactory.createSocket(socket, host, port, autoClose);
  }

  @Override
  @NotNull
  public String[] getDefaultCipherSuites() {
    return myFactory.getDefaultCipherSuites();
  }

  @Override
  @NotNull
  public String[] getSupportedCipherSuites() {
    return myFactory.getSupportedCipherSuites();
  }
}