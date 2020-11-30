// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.loader;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.lang.ClassPath;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/**
 * Pass '-Dintellij.class.resources.loading.logger=com.intellij.util.loader.LoadedResourcesDumper' JVM option to dump relative paths of loaded classes
 * to order.txt file.
 */
@SuppressWarnings("unused")
public final class LoadedResourcesDumper implements ClassPath.ResourceLoadingLogger {
  private BufferedWriter myOrder;
  private long myOrderSize;
  private final Set<String> myOrderedUrls = new HashSet<>();

  @Override
  public void logResource(String url, URL baseLoaderURL, long resourceSize) {
    if (!myOrderedUrls.add(url)) {
      return;
    }

    String home = PathManager.getHomePath().replace('\\', '/');
    if (resourceSize != -1) {
      myOrderSize += resourceSize;
    }

    BufferedWriter order = myOrder;
    if (order == null) {
      Path orderFile = Paths.get(PathManager.getBinPath(), "order.txt");
      try {
        order = Files.newBufferedWriter(orderFile, StandardOpenOption.APPEND, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        myOrder = order;
        ShutDownTracker.getInstance().registerShutdownTask(this::closeOrderStream);
      }
      catch (IOException e) {
        return;
      }
    }

    String jarURL = PathManager.splitJarUrl(baseLoaderURL.toExternalForm());
    if (jarURL == null || !jarURL.startsWith(home)) {
      return;
    }

    jarURL = jarURL.replaceFirst(home, "");
    //noinspection SSBasedInspection
    if (jarURL.endsWith("!/")) {
      jarURL = jarURL.substring(0, jarURL.length() - 2);
    }
    try {
      myOrder.write(url + ":" + jarURL + "\n");
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private synchronized void closeOrderStream() {
    try {
      myOrder.close();
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    System.out.println(myOrderSize);
  }
}
