/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;

class FileLoader extends Loader {
  private final File myRootDir;
  private final String myRootDirAbsolutePath;
  private static int misses;
  private static int hits;

  @SuppressWarnings({"HardCodedStringLiteral"})
  FileLoader(URL url, int index) throws IOException {
    super(url, index);
    if (!"file".equals(url.getProtocol())) {
      throw new IllegalArgumentException("url");
    }
    else {
      final String s = FileUtil.unquote(url.getFile());
      myRootDir = new File(s);
      myRootDirAbsolutePath = myRootDir.getAbsolutePath();
    }
  }

  // True -> class file
  private void buildPackageCache(final File dir, ClasspathCache cache) {
    cache.addResourceEntry(getRelativeResourcePath(dir), this);

    final File[] files = dir.listFiles();
    if (files == null) {
      return;
    }

    boolean containsClasses = false;
    for (File file : files) {
      final boolean isClass = file.getPath().endsWith(UrlClassLoader.CLASS_EXTENSION);
      if (isClass) {
        if (!containsClasses) {
          cache.addResourceEntry(getRelativeResourcePath(file), this);
          containsClasses = true;
        }
        cache.addNameEntry(file.getName(), this);
      }
      else {
        cache.addNameEntry(file.getName(), this);
        buildPackageCache(file, cache);
      }
    }
  }

  private String getRelativeResourcePath(final File file) {
    String relativePath = file.getAbsolutePath().substring(myRootDirAbsolutePath.length());
    relativePath = relativePath.replace(File.separatorChar, '/');
    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
    return relativePath;
  }

  @Nullable
  Resource getResource(final String name, boolean flag) {
    try {
      final URL url = new URL(getBaseURL(), name);
      if (!url.getFile().startsWith(getBaseURL().getFile())) return null;

      final File file = new File(myRootDir, name.replace('/', File.separatorChar));
      if (file.exists()) {
        ++hits;
        if (hits % 1000 == 0 && UrlClassLoader.doDebug) {
          UrlClassLoader.debug("Exists file loader: misses:" + misses + ", hits:" + hits);
        }
        return new MyResource(name, url, file);
      }

      if (misses % 1000 == 0 && UrlClassLoader.doDebug) {
        UrlClassLoader.debug("Missed " + name + " from " + myRootDir);
      }
      ++misses;
    }
    catch (Exception exception) {
      return null;
    }
    return null;
  }

  void buildCache(final ClasspathCache cache) throws IOException {
    File index = new File(myRootDir, "classpath.index");
    if (index.exists()) {
      BufferedReader reader = new BufferedReader(new FileReader(index));
      try {
        do {
          String line = reader.readLine();
          if (line == null) break;
          cache.addResourceEntry(line, this);
        }
        while (true);
      }
      finally {
        reader.close();
      }
    }
    else {
      cache.addResourceEntry("foo.class", this);
      cache.addResourceEntry("bar.properties", this);
      buildPackageCache(myRootDir, cache);
    }
  }

  private class MyResource extends Resource {
    private final String myName;
    private final URL myUrl;
    private final File myFile;

    public MyResource(String name, URL url, File file) {
      myName = name;
      myUrl = url;
      myFile = file;
    }

    public String getName() {
      return myName;
    }

    public URL getURL() {
      return myUrl;
    }

    public URL getCodeSourceURL() {
      return getBaseURL();
    }

    public InputStream getInputStream() throws IOException {
      return new BufferedInputStream(new FileInputStream(myFile));
    }

    public int getContentLength() throws IOException {
      return -1;
    }

    public String toString() {
      return myFile.getAbsolutePath();
    }
  }

  @NonNls
  public String toString() {
    return "FileLoader [" + myRootDir + "]";
  }
}
