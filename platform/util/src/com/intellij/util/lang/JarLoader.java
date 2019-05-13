// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.reference.SoftReference;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.openapi.util.Pair.pair;

class JarLoader extends Loader {
  private static final List<Pair<Resource.Attribute, Attributes.Name>> PACKAGE_FIELDS = Arrays.asList(
    pair(Resource.Attribute.SPEC_TITLE, Attributes.Name.SPECIFICATION_TITLE),
    pair(Resource.Attribute.SPEC_VERSION, Attributes.Name.SPECIFICATION_VERSION),
    pair(Resource.Attribute.SPEC_VENDOR, Attributes.Name.SPECIFICATION_VENDOR),
    pair(Resource.Attribute.IMPL_TITLE, Attributes.Name.IMPLEMENTATION_TITLE),
    pair(Resource.Attribute.IMPL_VERSION, Attributes.Name.IMPLEMENTATION_VERSION),
    pair(Resource.Attribute.IMPL_VENDOR, Attributes.Name.IMPLEMENTATION_VENDOR));

  private final String myFilePath;
  private final ClassPath myConfiguration;
  private final URL myUrl;
  private SoftReference<JarMemoryLoader> myMemoryLoader;
  private volatile SoftReference<ZipFile> myZipFileSoftReference; // Used only when myConfiguration.myCanLockJars==true
  private volatile Map<Resource.Attribute, String> myAttributes;
  private volatile String myClassPathManifestAttribute;
  private static final String NULL_STRING = "<null>";

  JarLoader(URL url, int index, ClassPath configuration) throws IOException {
    super(new URL("jar", "", -1, url + "!/"), index);

    myFilePath = urlToFilePath(url);
    myConfiguration = configuration;
    myUrl = url;

    if (!configuration.myLazyClassloadingCaches) {
      ZipFile zipFile = getZipFile(); // IOException from opening is propagated to caller if zip file isn't valid,
      try {
        if (configuration.myPreloadJarContents) {
          JarMemoryLoader loader = JarMemoryLoader.load(zipFile, getBaseURL(), this);
          if (loader != null) {
            myMemoryLoader = new SoftReference<JarMemoryLoader>(loader);
          }
        }
      }
      finally {
        releaseZipFile(zipFile);
      }
    }
  }

  Map<Resource.Attribute, String> getAttributes() {
    loadManifestAttributes();
    return myAttributes;
  }

  @Nullable
  String getClassPathManifestAttribute() {
    loadManifestAttributes();
    String manifestAttribute = myClassPathManifestAttribute;
    return manifestAttribute != NULL_STRING ? manifestAttribute : null;
  }

  private static String urlToFilePath(URL url) {
    try {
      return new File(url.toURI()).getPath();
    } catch (Throwable ignore) { // URISyntaxException or IllegalArgumentException
      return url.getPath();
    }
  }

  @Nullable
  private static Map<Resource.Attribute, String> getAttributes(@Nullable Attributes attributes) {
    if (attributes == null) return null;
    Map<Resource.Attribute, String> map = null;

    for (Pair<Resource.Attribute, Attributes.Name> p : PACKAGE_FIELDS) {
      String value = attributes.getValue(p.second);
      if (value != null) {
        if (map == null) map = new EnumMap<Resource.Attribute, String>(Resource.Attribute.class);
        map.put(p.first, value);
      }
    }

    return map;
  }

  private void loadManifestAttributes() {
    if (myClassPathManifestAttribute != null) return;
    synchronized (this) {
      try {
        if (myClassPathManifestAttribute != null) return;
        ZipFile zipFile = getZipFile();
        try {
          Attributes manifestAttributes = myConfiguration.getManifestData(myUrl);
          if (manifestAttributes == null) {
            ZipEntry entry = zipFile.getEntry(JarFile.MANIFEST_NAME);
            manifestAttributes = loadManifestAttributes(entry != null ? zipFile.getInputStream(entry) : null);
            if (manifestAttributes == null) manifestAttributes = new Attributes(0);
            myConfiguration.cacheManifestData(myUrl, manifestAttributes);
          }

          myAttributes = getAttributes(manifestAttributes);
          Object attribute = manifestAttributes.get(Attributes.Name.CLASS_PATH);
          myClassPathManifestAttribute = attribute instanceof String ? (String)attribute : NULL_STRING;
        }
        finally {
          releaseZipFile(zipFile);
        }
      } catch (IOException io) {
        throw new RuntimeException(io);
      }
    }
  }

  @Nullable
  private static Attributes loadManifestAttributes(@Nullable InputStream stream) {
    if (stream == null) return null;
    try {
      try {
        return new Manifest(stream).getMainAttributes();
      }
      finally {
        stream.close();
      }
    }
    catch (Exception ignored) { }
    return null;
  }

  @NotNull
  @Override
  public ClasspathCache.LoaderData buildData() throws IOException {
    ZipFile zipFile = getZipFile();
    try {
      ClasspathCache.LoaderDataBuilder loaderDataBuilder = new ClasspathCache.LoaderDataBuilder();
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();

        if (name.endsWith(UrlClassLoader.CLASS_EXTENSION)) {
          loaderDataBuilder.addClassPackageFromName(name);
        } else {
          loaderDataBuilder.addResourcePackageFromName(name);
        }

        loaderDataBuilder.addPossiblyDuplicateNameEntry(name);
      }

      return loaderDataBuilder.build();
    }
    finally {
      releaseZipFile(zipFile);
    }
  }

  private final AtomicInteger myNumberOfRequests = new AtomicInteger();
  private volatile TIntHashSet myPackageHashesInside;

  private TIntHashSet buildPackageHashes() {
    try {
      ZipFile zipFile = getZipFile();
      try {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        TIntHashSet result = new TIntHashSet();

        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          result.add(ClasspathCache.getPackageNameHash(entry.getName()));
        }
        result.add(0); // empty package is in every jar
        return result;
      }
      finally {
        releaseZipFile(zipFile);
      }
    } catch (Exception e) {
      error("url: " + myFilePath, e);
      return new TIntHashSet(0);
    }
  }

  @Override
  @Nullable
  Resource getResource(String name) {
    if (myConfiguration.myLazyClassloadingCaches) {
      int numberOfHits = myNumberOfRequests.incrementAndGet();
      TIntHashSet packagesInside = myPackageHashesInside;

      if (numberOfHits > ClasspathCache.NUMBER_OF_ACCESSES_FOR_LAZY_CACHING && packagesInside == null) {
        myPackageHashesInside = packagesInside = buildPackageHashes();
      }

      if (packagesInside != null && !packagesInside.contains(ClasspathCache.getPackageNameHash(name))) {
        return null;
      }
    }

    JarMemoryLoader loader = myMemoryLoader != null ? myMemoryLoader.get() : null;
    if (loader != null) {
      Resource resource = loader.getResource(name);
      if (resource != null) return resource;
    }

    try {
      ZipFile zipFile = getZipFile();
      try {
        ZipEntry entry = zipFile.getEntry(name);
        if (entry != null) {
          return new MyResource(getBaseURL(), entry);
        }
      }
      finally {
        releaseZipFile(zipFile);
      }
    }
    catch (Exception e) {
      error("url: " + myFilePath, e);
    }

    return null;
  }

  private class MyResource extends Resource {
    private final URL myUrl;
    private final ZipEntry myEntry;

    MyResource(URL url, ZipEntry entry) throws IOException {
      myUrl = new URL(url, entry.getName());
      myEntry = entry;
    }

    @Override
    public URL getURL() {
      return myUrl;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(getBytes());
    }

    @Override
    public byte[] getBytes() throws IOException {
      ZipFile file = getZipFile();
      InputStream stream = null;
      try {
        stream = file.getInputStream(myEntry);
        return FileUtil.loadBytes(stream, (int)myEntry.getSize());
      } finally {
        if (stream != null) stream.close();
        releaseZipFile(file);
      }
    }

    @Override
    public String getValue(Attribute key) {
      loadManifestAttributes();
      return myAttributes != null ? myAttributes.get(key) : null;
    }
  }

  protected void error(String message, Throwable t) {
    if (myConfiguration.myLogErrorOnMissingJar) {
      Logger.getInstance(JarLoader.class).error(message, t);
    }
    else {
      Logger.getInstance(JarLoader.class).warn(message, t);
    }
  }

  private static final Object ourLock = new Object();

  @NotNull
  private ZipFile getZipFile() throws IOException {
    // This code is executed at least 100K times (O(number of classes needed to load)) and it takes considerable time to open ZipFile's
    // such number of times so we store reference to ZipFile if we allowed to lock the file (assume it isn't changed)
    if (myConfiguration.myCanLockJars) {
      ZipFile zipFile = SoftReference.dereference(myZipFileSoftReference);
      if (zipFile != null) return zipFile;

      synchronized (ourLock) {
        zipFile = SoftReference.dereference(myZipFileSoftReference);
        if (zipFile != null) return zipFile;

        // ZipFile's native implementation (ZipFile.c, zip_util.c) has path -> file descriptor cache
        zipFile = new ZipFile(myFilePath);
        myZipFileSoftReference = new SoftReference<ZipFile>(zipFile);
        return zipFile;
      }
    }
    else {
      return new ZipFile(myFilePath);
    }
  }

  private void releaseZipFile(ZipFile zipFile) throws IOException {
    // Closing of zip file when myConfiguration.myCanLockJars=true happens in ZipFile.finalize
    if (!myConfiguration.myCanLockJars) {
      zipFile.close();
    }
  }

  @Override
  public String toString() {
    return "JarLoader [" + myFilePath + "]";
  }
}
