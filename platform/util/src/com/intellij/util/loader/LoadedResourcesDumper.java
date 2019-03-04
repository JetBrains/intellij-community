// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.loader;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.ClassPath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Pass '-Dintellij.class.resources.loading.logger=com.intellij.util.loader.LoadedResourcesDumper' JVM option to dump relative paths of loaded classes
 * to order.txt file.
 */
@SuppressWarnings("unused")
public class LoadedResourcesDumper implements ClassPath.ResourceLoadingLogger {
  private PrintStream myOrder;
  private long myOrderSize;
  private final Set<String> myOrderedUrls = new HashSet<>();

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  @Override
  public void logResource(String url, URL baseLoaderURL, long resourceSize) {
    if (!myOrderedUrls.add(url)) return;

    String home = FileUtil.toSystemIndependentName(PathManager.getHomePath());
    if (resourceSize != -1) {
      myOrderSize += resourceSize;
    }

    if (myOrder == null) {
      final File orderFile = new File(PathManager.getBinPath(), "order.txt");
      try {
        if (!FileUtil.ensureCanCreateFile(orderFile)) return;
        myOrder = new PrintStream(new FileOutputStream(orderFile, true));
        ShutDownTracker.getInstance().registerShutdownTask(this::closeOrderStream);
      }
      catch (IOException e) {
        return;
      }
    }

    if (myOrder != null) {
      Pair<String, String> pair = URLUtil.splitJarUrl(baseLoaderURL.toExternalForm());
      String jarURL = pair != null ? pair.first : null;
      if (jarURL != null && jarURL.startsWith(home)) {
        jarURL = jarURL.replaceFirst(home, "");
        jarURL = StringUtil.trimEnd(jarURL, "!/");
        myOrder.println(url + ":" + jarURL);
      }
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private synchronized void closeOrderStream() {
    myOrder.close();
    System.out.println(myOrderSize);
  }
}
