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
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author max
 */
public class ExternalClasspathClassLoader extends URLClassLoader {
  private ExternalClasspathClassLoader(URL[] urls) {
    super(urls, Thread.currentThread().getContextClassLoader());
  }

  private static String[] parseUrls(String classpathFilePath) {
    Collection<String> roots = new LinkedHashSet<>();
    File file = new File(classpathFilePath);
    try {
      final BufferedReader reader = new BufferedReader(new FileReader(file));
      try {
        while (reader.ready()) {
          roots.add(reader.readLine());
        }
      }
      finally {
        reader.close();
      }

      //noinspection SSBasedInspection
      return roots.toArray(new String[roots.size()]);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String[] getRoots() {
    final String classPathFilePath = System.getProperty("classpath.file");
    return classPathFilePath != null ? parseUrls(classPathFilePath) : null;
  }

  public static String[] getExcludeRoots() {
    try {
      final String classPathFilePath = System.getProperty("exclude.tests.roots.file");
      return classPathFilePath != null ? parseUrls(classPathFilePath) : null;
    }
    catch (Exception e) {
      //noinspection SSBasedInspection
      return new String[0];
    }
  }

  public static void install() {
    try {
      URL[] urls = parseUrls();
      if (urls != null) {
        Thread.currentThread().setContextClassLoader(new ExternalClasspathClassLoader(urls));
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static URL[] parseUrls() {
    try {
      String[] roots = getRoots();
      if (roots == null) return null;

      URL[] urls = new URL[roots.length];
      for (int i = 0; i < urls.length; i++) {
        urls[i] = new File(roots[i]).toURI().toURL();
      }
      return urls;
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
