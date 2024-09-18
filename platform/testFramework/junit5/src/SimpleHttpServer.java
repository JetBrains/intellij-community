// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;

@SuppressWarnings("HttpUrlsUsage")
public class SimpleHttpServer implements BeforeEachCallback, AfterEachCallback {
  private static final String LOCALHOST = "127.0.0.1";

  public HttpServer server;
  public String url;

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    server = HttpServer.create();
    server.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    server.start();
    url = "http://%s:%s".formatted(LOCALHOST, server.getAddress().getPort());
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    if (server != null) {
      url = null;
      server.stop(0);
      server = null;
    }
  }

  public @NotNull HttpContext createContext(@NotNull String path, @NotNull HttpHandler handler) {
    return server.createContext(path, handler);
  }
}
