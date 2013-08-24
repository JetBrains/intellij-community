/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.TimedComputable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.*;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private final URL myURL;
  private SoftReference<JarMemoryLoader> myMemoryLoader;
  private final boolean myCanLockJar;
  private static final boolean myDebugTime = false;
  private static int misses;
  private static int hits;

  private static final Logger LOG = Logger.getInstance(JarLoader.class);

  private final TimedComputable<ZipFile> myZipFileRef = new TimedComputable<ZipFile>(null) {
    @Override
    @NotNull
    protected ZipFile calc() {
      try {
        final ZipFile zipFile = doGetZipFile();
        if (zipFile == null) throw new RuntimeException("Can't load zip file");
        return zipFile;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  @NonNls private static final String JAR_PROTOCOL = "jar";
  @NonNls private static final String FILE_PROTOCOL = "file";
  private static final long NS_THRESHOLD = 10000000;

  JarLoader(URL url, boolean canLockJar, int index) throws IOException {
    super(new URL(JAR_PROTOCOL, "", -1, url + "!/"), index);
    myURL = url;
    myCanLockJar = canLockJar;
  }

  void preLoadClasses() {
    ZipFile zipFile = null;
    try {
      zipFile = acquireZipFile();
      if (zipFile == null) return;
      try {
        File file = new File(zipFile.getName());
        myMemoryLoader = new SoftReference<JarMemoryLoader>(JarMemoryLoader.load(file, getBaseURL()));
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    catch (Exception e) {
      // it happens :) eg tools.jar under MacOS
    }
    finally {
      try {
        releaseZipFile(zipFile);
      }
      catch (IOException ignore) {

      }
    }
  }

  @Nullable
  private ZipFile acquireZipFile() throws IOException {
    if (myCanLockJar) {
      return myZipFileRef.acquire();
    }
    return doGetZipFile();
  }

  private void releaseZipFile(final ZipFile zipFile) throws IOException {
    if (myCanLockJar) {
      myZipFileRef.release();
    }
    else if (zipFile != null) {
      zipFile.close();
    }
  }

  @Nullable
  private ZipFile doGetZipFile() throws IOException {
    if (FILE_PROTOCOL.equals(myURL.getProtocol())) {
      String s = FileUtil.unquote(myURL.getFile());
      if (!new File(s).exists()) {
        throw new FileNotFoundException(s);
      }
      else {
        return new ZipFile(s);
      }
    }

    return null;
  }

  @Override
  void buildCache(final ClasspathCache cache) throws IOException {
    ZipFile zipFile = null;
    try {
      zipFile = acquireZipFile();
      if (zipFile == null) return;
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();

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
    final long started = myDebugTime ? System.nanoTime():0;
    if (myMemoryLoader != null) {
      JarMemoryLoader loader = myMemoryLoader.get();
      if (loader != null) {
        Resource resource = loader.getResource(name);
        if (resource != null) return resource;
      }
    }
    ZipFile file = null;
    try {
      file = acquireZipFile();
      if (file == null) return null;
      ZipEntry entry = file.getEntry(name);
      if (entry != null) {
        ++hits;
        if (hits % 1000 == 0 && ClasspathCache.doDebug) {
          ClasspathCache.LOG.debug("Exists jar loader: misses:" + misses + ", hits:" + hits);
        }
        return new MyResource(entry, new URL(getBaseURL(), name));
      }

      if (misses % 1000 == 0 && ClasspathCache.doDebug) {
        ClasspathCache.LOG.debug("Missed " + name + " from jar:" + myURL);
      }
      ++misses;
    }
    catch (Exception e) {
      return null;
    }
    finally {
      try {
        releaseZipFile(file);
      }
      catch (IOException ignored) {
      }
      final long doneFor = myDebugTime ? System.nanoTime() - started :0;
      if (doneFor > NS_THRESHOLD) {
        ClasspathCache.LOG.debug(doneFor/1000000 + " ms for jar loader get resource:"+name);
      }
    }

    return null;
  }

  private class MyResource extends Resource {
    private final ZipEntry myEntry;
    private final URL myUrl;

    public MyResource(ZipEntry name, URL url) {
      myEntry = name;
      myUrl = url;
    }

    @Override
    public String getName() {
      return myEntry.getName();
    }

    @Override
    public URL getURL() {
      return myUrl;
    }

    @Override
    public URL getCodeSourceURL() {
      return myURL;
    }

    @Override
    @Nullable
    public InputStream getInputStream() throws IOException {
      final boolean[] wasReleased = {false};
      ZipFile file = null;

      try {
        file = acquireZipFile();
        if (file == null) {
          releaseZipFile(file);
          return null;
        }

        final InputStream inputStream = file.getInputStream(myEntry);
        if (inputStream == null) {
          releaseZipFile(file);
          return null; // if entry was not found
        }
        final ZipFile finalFile = file;
        return new FilterInputStream(inputStream) {
          private boolean myClosed = false;
          @Override
          public void close() throws IOException {
            super.close();
            if (!myClosed) {
              releaseZipFile(finalFile);
            }
            myClosed = true;
            wasReleased[0] = true;
          }
        };
      }
      catch (IOException e) {
        e.printStackTrace();
        releaseZipFile(file);
        assert !wasReleased[0];
        return null;
      }
    }

    @Override
    public int getContentLength() {
      return (int)myEntry.getSize();
    }
  }

  @NonNls
  public String toString() {
    return "JarLoader [" + myURL + "]";
  }
}
