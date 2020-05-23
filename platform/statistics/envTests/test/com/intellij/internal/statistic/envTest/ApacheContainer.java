// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.net.MalformedURLException;
import java.net.URL;

public final class ApacheContainer extends GenericContainer<ApacheContainer> {
  private static final int DEFAULT_PORT = 80;
  private static final String REMOTE_ROOT = "/var/www/html";

  ApacheContainer(@NotNull String localPath) {
    super("php:7.1-apache");
    addFileSystemBind(localPath, REMOTE_ROOT, BindMode.READ_ONLY);
  }

  @Override
  protected void configure() {
    addExposedPort(DEFAULT_PORT);
  }

  @NotNull
  public URL getBaseUrl(@Nullable String path) throws MalformedURLException {
    String base = "http://" + getContainerIpAddress() + ":" + getMappedPort(DEFAULT_PORT) + "/";
    if (StringUtil.isNotEmpty(path)) {
      base += path;
    }
    return new URL(base);
  }
}