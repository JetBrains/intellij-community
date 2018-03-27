/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author max
 */
public class ExternalClasspathClassLoader {

  private static List<File> loadFilesPaths(String classpathFilePath) {
    try {
      File file = new File(classpathFilePath);
      Set<File> roots = new LinkedHashSet<>();
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
        while (reader.ready()) {
          roots.add(new File(reader.readLine()));
        }
      }
      return new ArrayList<>(roots);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<File> getRoots() {
    final String classPathFilePath = System.getProperty("classpath.file");
    return classPathFilePath != null ? loadFilesPaths(classPathFilePath) : null;
  }

  public static List<File> getExcludeRoots() {
    try {
      final String classPathFilePath = System.getProperty("exclude.tests.roots.file");
      return classPathFilePath != null ? loadFilesPaths(classPathFilePath) : null;
    }
    catch (Exception e) {
      return Collections.emptyList();
    }
  }

  public static void install() {
    try {
      URL[] urls = parseUrls();
      if (urls != null) {
        URLClassLoader auxLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(auxLoader);
        Thread.currentThread().setContextClassLoader(loadOptimizedLoader(urls, auxLoader));
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static ClassLoader loadOptimizedLoader(Object urls, URLClassLoader auxLoader) throws Exception {
    Object builder = auxLoader.loadClass("com.intellij.util.lang.UrlClassLoader").getMethod("build").invoke(null);
    builder.getClass().getMethod("urls", URL[].class).invoke(builder, urls);
    builder.getClass().getMethod("useCache").invoke(builder);
    builder.getClass().getMethod("allowLock").invoke(builder);
    builder.getClass().getMethod("allowBootstrapResources").invoke(builder);
    builder.getClass().getMethod("parent", ClassLoader.class).invoke(builder, auxLoader.getParent());
    return (ClassLoader)builder.getClass().getMethod("get").invoke(builder);
  }

  private static URL[] parseUrls() {
    try {
      List<File> roots = getRoots();
      if (roots == null) return null;

      URL[] urls = new URL[roots.size()];
      for (int i = 0; i < urls.length; i++) {
        urls[i] = roots.get(i).toURI().toURL();
      }
      return urls;
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
