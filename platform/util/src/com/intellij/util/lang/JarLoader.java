/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private final URL myURL;
  private SoftReference<JarMemoryLoader> myMemoryLoader;

  // todo drop unused parameter
  JarLoader(URL url, @SuppressWarnings("unused") boolean canLockJar, int index, boolean preloadJarContents) throws IOException {
    super(new URL(URLUtil.JAR_PROTOCOL, "", -1, url + "!/"), index);
    myURL = url;

    ZipFile zipFile = new ZipFile(getFileUrl());
    try {
      if (preloadJarContents) {
        JarMemoryLoader loader = JarMemoryLoader.load(zipFile, getBaseURL());
        if (loader != null) {
          myMemoryLoader = new SoftReference<JarMemoryLoader>(loader);
        }
      }
    }
    finally {
      zipFile.close();
    }
  }

  private File getFileUrl() throws IOException {
    try {
      return new File(myURL.toURI());
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  @NotNull
  @Override
  public ClasspathCache.LoaderData buildData() throws IOException {
    ZipFile zipFile = new ZipFile(getFileUrl());
    try {
      ClasspathCache.LoaderData loaderData = new ClasspathCache.LoaderData();
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        loaderData.addResourceEntry(name);
        loaderData.addNameEntry(name);
      }
      return loaderData;
    }
    finally {
      zipFile.close();
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
      ZipFile zipFile = new ZipFile(getFileUrl());
      try {
        ZipEntry entry = zipFile.getEntry(name);
        if (entry != null) {
          return MemoryResource.load(getBaseURL(), zipFile, entry);
        }
      }
      finally {
        zipFile.close();
      }
    }
    catch (Exception e) {
      Logger.getInstance(JarLoader.class).error("url: " + myURL, e);
    }

    return null;
  }

  @Override
  public String toString() {
    return "JarLoader [" + myURL + "]";
  }
}
