// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang.java6;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.lang.java6.fastutil.StrippedIntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private static final List<Map.Entry<Resource.Attribute, Attributes.Name>> PACKAGE_FIELDS = Arrays.<Map.Entry<Resource.Attribute, Attributes.Name>>asList(
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_TITLE, Attributes.Name.SPECIFICATION_TITLE),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_VERSION, Attributes.Name.SPECIFICATION_VERSION),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_VENDOR, Attributes.Name.SPECIFICATION_VENDOR),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_TITLE, Attributes.Name.IMPLEMENTATION_TITLE),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_VERSION, Attributes.Name.IMPLEMENTATION_VERSION),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_VENDOR, Attributes.Name.IMPLEMENTATION_VENDOR));

  private static final String NULL_STRING = "<null>";

  private final String myFilePath;
  private final ClassPath myConfiguration;
  private final URL myUrl;
  private volatile SoftReference<ZipFile> myZipFileSoftReference; // Used only when myConfiguration.myCanLockJars==true
  private volatile Map<Resource.Attribute, String> myAttributes;
  private volatile String myClassPathManifestAttribute;

  JarLoader(@NotNull URL url, @NotNull String filePath, @NotNull ClassPath configuration) throws IOException {
    super(new URL("jar", "", -1, url + "!/"));

    myFilePath = filePath;
    myConfiguration = configuration;
    myUrl = url;

    if (!configuration.myLazyClassloadingCaches) {
      // IOException from opening is propagated to caller if zip file isn't valid,
      ZipFile zipFile = getZipFile();
      releaseZipFile(zipFile);
    }
  }

  @Nullable
  String getClassPathManifestAttribute() {
    loadManifestAttributes();
    String manifestAttribute = myClassPathManifestAttribute;
    //noinspection StringEquality
    return manifestAttribute != NULL_STRING ? manifestAttribute : null;
  }

  @Nullable
  private static Map<Resource.Attribute, String> getAttributes(@Nullable Attributes attributes) {
    if (attributes == null) return null;
    Map<Resource.Attribute, String> map = null;

    for (Map.Entry<Resource.Attribute, Attributes.Name> p : PACKAGE_FIELDS) {
      String value = attributes.getValue(p.getValue());
      if (value != null) {
        if (map == null) {
          map = new EnumMap<>(Resource.Attribute.class);
        }
        map.put(p.getKey(), value);
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
            if (entry != null) manifestAttributes = loadManifestAttributes(zipFile.getInputStream(entry));
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
      }
      catch (IOException io) {
        throw new RuntimeException(io);
      }
    }
  }

  @Nullable
  private static Attributes loadManifestAttributes(InputStream stream) {
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
  private volatile StrippedIntOpenHashSet myPackageHashesInside;

  private @NotNull
  StrippedIntOpenHashSet buildPackageHashes() {
    try {
      ZipFile zipFile = getZipFile();
      try {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        StrippedIntOpenHashSet result = new StrippedIntOpenHashSet();

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
    }
    catch (Exception e) {
      error("url: " + myFilePath, e);
      return new StrippedIntOpenHashSet(0);
    }
  }

  @Override
  @Nullable
  Resource getResource(@NotNull String name) {
    if (myConfiguration.myLazyClassloadingCaches) {
      int numberOfHits = myNumberOfRequests.incrementAndGet();
      StrippedIntOpenHashSet packagesInside = myPackageHashesInside;

      if (numberOfHits > ClasspathCache.NUMBER_OF_ACCESSES_FOR_LAZY_CACHING && packagesInside == null) {
        myPackageHashesInside = packagesInside = buildPackageHashes();
      }

      if (packagesInside != null && !packagesInside.contains(ClasspathCache.getPackageNameHash(name))) {
        return null;
      }
    }

    try {
      ZipFile zipFile = getZipFile();
      try {
        ZipEntry entry = zipFile.getEntry(name);
        if (entry != null) {
          return instantiateResource(getBaseURL(), entry);
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

  @NotNull
  protected Resource instantiateResource(@NotNull URL url, @NotNull ZipEntry entry) throws IOException {
    return new MyResource(url,entry);
  }

  protected class MyResource extends Resource {
    protected final URL myUrl;
    protected final ZipEntry myEntry;

    MyResource(@NotNull URL url, @NotNull ZipEntry entry) throws IOException {
      myUrl = new URL(url, entry.getName());
      myEntry = entry;
    }

    @NotNull
    @Override
    public URL getURL() {
      return myUrl;
    }

    @NotNull
    @Override
    public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(getBytes());
    }

    @NotNull
    @Override
    public byte[] getBytes() throws IOException {
      ZipFile file = getZipFile();
      try (InputStream stream = file.getInputStream(myEntry)) {
        return FileUtilRt.loadBytes(stream, (int)myEntry.getSize());
      }
      finally {
        releaseZipFile(file);
      }
    }

    @Override
    public String getValue(@NotNull Attribute key) {
      loadManifestAttributes();
      return myAttributes != null ? myAttributes.get(key) : null;
    }
  }

  protected void error(@NotNull String message, @NotNull Throwable t) {
    if (myConfiguration.myLogErrorOnMissingJar) {
      LoggerRt.getInstance(JarLoader.class).error(message, t);
    }
    else {
      LoggerRt.getInstance(JarLoader.class).warn(message, t);
    }
  }

  private static final Object ourLock = new Object();

  protected @NotNull ZipFile getZipFile() throws IOException {
    // This code is executed at least 100K times (O(number of classes needed to load)) and it takes considerable time to open ZipFile's
    // such number of times so we store reference to ZipFile if we allowed to lock the file (assume it isn't changed)
    if (myConfiguration.myCanLockJars) {
      SoftReference<ZipFile> ref = myZipFileSoftReference;
      ZipFile zipFile = ref == null ? null : ref.get();
      if (zipFile != null) {
        return zipFile;
      }

      synchronized (ourLock) {
        ref = myZipFileSoftReference;
        zipFile = ref == null ? null : ref.get();
        if (zipFile != null) {
          return zipFile;
        }

        // ZipFile's native implementation (ZipFile.c, zip_util.c) has path -> file descriptor cache
        zipFile = createZipFile(myFilePath);
        myZipFileSoftReference = new SoftReference<>(zipFile);
        return zipFile;
      }
    }
    return createZipFile(myFilePath);
  }

  @NotNull
  protected ZipFile createZipFile(@NotNull String path) throws IOException {
    return new ZipFile(path);
  }

  protected void releaseZipFile(@NotNull ZipFile zipFile) throws IOException {
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