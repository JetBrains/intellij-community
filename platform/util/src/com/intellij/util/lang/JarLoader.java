/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.ZipFileCache;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private static final Logger LOG = Logger.getInstance(JarLoader.class);

  private final URL myURL;
  private final boolean myCanLockJar;
  private SoftReference<JarMemoryLoader> myMemoryLoader;

  JarLoader(URL url, boolean canLockJar, int index) throws IOException {
    super(new URL(URLUtil.JAR_PROTOCOL, "", -1, url + "!/"), index);
    myURL = url;
    myCanLockJar = canLockJar;
  }

  private ZipFile acquireZipFile() throws IOException {
    String path = FileUtil.unquote(myURL.getFile());
    //noinspection IOResourceOpenedButNotSafelyClosed
    return myCanLockJar ? ZipFileCache.acquire(path) : new ZipFile(path);
  }

  private void releaseZipFile(ZipFile zipFile) throws IOException {
    if (myCanLockJar) {
      ZipFileCache.release(zipFile);
    }
    else if (zipFile != null) {
      zipFile.close();
    }
  }

  void preloadClasses() {
    try {
      ZipFile zipFile = acquireZipFile();
      try {
        JarMemoryLoader loader = JarMemoryLoader.load(zipFile, getBaseURL());
        if (loader != null) {
          myMemoryLoader = new SoftReference<JarMemoryLoader>(loader);
        }
      }
      finally {
        releaseZipFile(zipFile);
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  void buildCache(ClasspathCache cache) throws IOException {
    ZipFile zipFile = acquireZipFile();
    try {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        String name = zipEntry.getName();
        cache.addResourceEntry(name, this);
        cache.addNameEntry(name, this);
      }
    }
    finally {
      releaseZipFile(zipFile);
    }
  }

  @Override
  @Nullable
  Resource getResource(String name, boolean flag) {
    JarMemoryLoader loader = com.intellij.reference.SoftReference.dereference(myMemoryLoader);
    if (loader != null) {
      Resource resource = loader.getResource(name);
      if (resource != null) return resource;
    }

    try {
      ZipFile file = acquireZipFile();
      try {
        ZipEntry entry = file.getEntry(name);
        if (entry != null) {
          return MemoryResource.load(getBaseURL(), file, entry);
        }
      }
      finally {
        releaseZipFile(file);
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }

    return null;
  }

  @Override
  public String toString() {
    return "JarLoader [" + myURL + "]";
  }
}
