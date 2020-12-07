// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class ExternalClasspathClassLoader {
  private static List<Path> loadFilesPaths(String classpathFilePath) {
    try {
      Path file = Paths.get(classpathFilePath);
      Set<Path> roots = new LinkedHashSet<>();
      try (BufferedReader reader = Files.newBufferedReader(file)) {
        while (reader.ready()) {
          roots.add(Paths.get(reader.readLine()));
        }
      }
      return new ArrayList<>(roots);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<Path> getRoots() {
    String classPathFilePath = System.getProperty("classpath.file");
    return classPathFilePath != null ? loadFilesPaths(classPathFilePath) : null;
  }

  public static List<Path> getExcludeRoots() {
    try {
      String classPathFilePath = System.getProperty("exclude.tests.roots.file");
      return classPathFilePath != null ? loadFilesPaths(classPathFilePath) : null;
    }
    catch (Exception e) {
      return Collections.emptyList();
    }
  }

  public static void install() {
    List<Path> files = getRoots();
    if (files == null) {
      return;
    }

    try {
      URL[] urls = files.stream().map(path -> {
        try {
          return path.toUri().toURL();
        }
        catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
      }).toArray(URL[]::new);
      URLClassLoader auxLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
      Thread.currentThread().setContextClassLoader(auxLoader);
      Thread.currentThread().setContextClassLoader(loadOptimizedLoader(files, auxLoader));
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static ClassLoader loadOptimizedLoader(Object files, URLClassLoader auxLoader) throws Exception {
    Object builder = auxLoader.loadClass("com.intellij.util.lang.UrlClassLoader").getMethod("build").invoke(null);
    builder.getClass().getMethod("files", List.class).invoke(builder, files);
    builder.getClass().getMethod("useCache").invoke(builder);
    builder.getClass().getMethod("allowBootstrapResources").invoke(builder);
    builder.getClass().getMethod("parent", ClassLoader.class).invoke(builder, auxLoader.getParent());
    return (ClassLoader)builder.getClass().getMethod("get").invoke(builder);
  }
}
