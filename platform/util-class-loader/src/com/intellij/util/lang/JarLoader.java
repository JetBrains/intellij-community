// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.util.lang.fastutil.StrippedIntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private static final List<Map.Entry<Resource.Attribute, Attributes.Name>> PACKAGE_FIELDS = Arrays.asList(
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_TITLE, Attributes.Name.SPECIFICATION_TITLE),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_VERSION, Attributes.Name.SPECIFICATION_VERSION),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_VENDOR, Attributes.Name.SPECIFICATION_VENDOR),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_TITLE, Attributes.Name.IMPLEMENTATION_TITLE),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_VERSION, Attributes.Name.IMPLEMENTATION_VERSION),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_VENDOR, Attributes.Name.IMPLEMENTATION_VENDOR));

  private static final String NULL_STRING = "<null>";
  private static final Object ourLock = new Object();

  private final ClassPath configuration;
  protected final URL url;
  private SoftReference<JarMemoryLoader> memoryLoader;
  // Used only when configuration.canLockJars==true
  private volatile SoftReference<ZipFile> zipFileSoftReference;
  private volatile Map<Resource.Attribute, String> attributes;
  private volatile String classPathManifestAttribute;

  private final AtomicInteger myNumberOfRequests = new AtomicInteger();
  private volatile StrippedIntOpenHashSet myPackageHashesInside;

  JarLoader(@NotNull Path file, @NotNull ClassPath configuration) throws IOException {
    super(file);

    this.configuration = configuration;
    this.url = new URL("jar", "", -1, "file:" + file + "!/");

    if (!configuration.lazyClassloadingCaches) {
      // IOException from opening is propagated to caller if zip file isn't valid,
      ZipFile zipFile = getZipFile();
      try {
        if (configuration.preloadJarContents) {
          JarMemoryLoader loader = JarMemoryLoader.load(zipFile, path, this);
          if (loader != null) {
            memoryLoader = new SoftReference<>(loader);
          }
        }
      }
      finally {
        releaseZipFile(zipFile);
      }
    }
  }

  final Map<Resource.Attribute, String> getAttributes() {
    loadManifestAttributes();
    return attributes;
  }

  final @Nullable String getClassPathManifestAttribute() {
    loadManifestAttributes();
    String manifestAttribute = classPathManifestAttribute;
    return manifestAttribute != NULL_STRING ? manifestAttribute : null;
  }

  private static @Nullable Map<Resource.Attribute, String> getAttributes(@Nullable Attributes attributes) {
    if (attributes == null) {
      return null;
    }
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
    if (classPathManifestAttribute != null) {
      return;
    }

    synchronized (this) {
      try {
        if (classPathManifestAttribute != null) {
          return;
        }

        ZipFile zipFile = getZipFile();
        try {
          Attributes manifestAttributes = configuration.getManifestData(path);
          if (manifestAttributes == null) {
            ZipEntry entry = zipFile.getEntry(JarFile.MANIFEST_NAME);
            if (entry != null) manifestAttributes = loadManifestAttributes(zipFile.getInputStream(entry));
            if (manifestAttributes == null) manifestAttributes = new Attributes(0);
            configuration.cacheManifestData(path, manifestAttributes);
          }

          attributes = getAttributes(manifestAttributes);
          Object attribute = manifestAttributes.get(Attributes.Name.CLASS_PATH);
          classPathManifestAttribute = attribute instanceof String ? (String)attribute : NULL_STRING;
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

  private static @Nullable Attributes loadManifestAttributes(InputStream stream) {
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

  @Override
  public final @NotNull ClasspathCache.LoaderData buildData() throws IOException {
    ZipFile zipFile = getZipFile();
    try {
      ClasspathCache.LoaderDataBuilder loaderDataBuilder = new ClasspathCache.LoaderDataBuilder();
      Enumeration<? extends ZipEntry> entries = zipFile.entries();

      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();

        if (name.endsWith(UrlClassLoader.CLASS_EXTENSION)) {
          loaderDataBuilder.addClassPackageFromName(name);
        }
        else {
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

  private @NotNull StrippedIntOpenHashSet buildPackageHashes() {
    try {
      ZipFile zipFile = getZipFile();
      try {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        StrippedIntOpenHashSet result = new StrippedIntOpenHashSet();

        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          String name = entry.getName();
          result.add(ClasspathCache.getPackageNameHash(name, name.lastIndexOf('/')));
        }
        result.add(0); // empty package is in every jar
        return result;
      }
      finally {
        releaseZipFile(zipFile);
      }
    }
    catch (Exception e) {
      error("url: " + path, e);
      return new StrippedIntOpenHashSet(0);
    }
  }

  @Override
  @Nullable Resource getResource(@NotNull String name) {
    if (configuration.lazyClassloadingCaches) {
      int numberOfHits = myNumberOfRequests.incrementAndGet();
      StrippedIntOpenHashSet packagesInside = myPackageHashesInside;

      if (numberOfHits > ClasspathCache.NUMBER_OF_ACCESSES_FOR_LAZY_CACHING && packagesInside == null) {
        myPackageHashesInside = packagesInside = buildPackageHashes();
      }

      if (packagesInside != null && !packagesInside.contains(ClasspathCache.getPackageNameHash(name, name.lastIndexOf('/')))) {
        return null;
      }
    }

    JarMemoryLoader loader = memoryLoader != null ? memoryLoader.get() : null;
    if (loader != null) {
      Resource resource = loader.getResource(name);
      if (resource != null) {
        return resource;
      }
    }

    try {
      ZipFile zipFile = getZipFile();
      try {
        ZipEntry entry = zipFile.getEntry(name);
        if (entry != null) {
          return instantiateResource(entry);
        }
      }
      finally {
        releaseZipFile(zipFile);
      }
    }
    catch (Exception e) {
      error("url: " + path, e);
    }

    return null;
  }

  protected @NotNull Resource instantiateResource(@NotNull ZipEntry entry) throws IOException {
    return new ZipFileResource(url, entry);
  }

  protected class ZipFileResource extends Resource {
    protected final URL baseUrl;
    private URL url;
    protected final ZipEntry entry;

    ZipFileResource(@NotNull URL baseUrl, @NotNull ZipEntry entry) {
      this.baseUrl = baseUrl;
      this.entry = entry;
    }

    @Override
    public @NotNull URL getURL() {
      URL result = url;
      if (result == null) {
        try {
          result = new URL(baseUrl, entry.getName());
        }
        catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
        url = result;
      }
      return result;
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(getBytes());
    }

    @Override
    public byte @NotNull [] getBytes() throws IOException {
      ZipFile file = getZipFile();
      try (InputStream stream = file.getInputStream(entry)) {
        return Resource.loadBytes(stream, (int)entry.getSize());
      }
      finally {
        releaseZipFile(file);
      }
    }

    @Override
    public String getValue(@NotNull Attribute key) {
      loadManifestAttributes();
      return attributes == null ? null : attributes.get(key);
    }
  }

  protected final void error(@NotNull String message, @NotNull Throwable t) {
    if (configuration.errorOnMissingJar) {
      LoggerRt.getInstance(JarLoader.class).error(message, t);
    }
    else {
      LoggerRt.getInstance(JarLoader.class).warn(message, t);
    }
  }

  protected final @NotNull ZipFile getZipFile() throws IOException {
    // This code is executed at least 100K times (O(number of classes needed to load)) and it takes considerable time to open ZipFile's
    // such number of times so we store reference to ZipFile if we allowed to lock the file (assume it isn't changed)
    if (configuration.lockJars) {
      SoftReference<ZipFile> ref = zipFileSoftReference;
      ZipFile zipFile = ref == null ? null : ref.get();
      if (zipFile != null) {
        return zipFile;
      }

      synchronized (ourLock) {
        ref = zipFileSoftReference;
        zipFile = ref == null ? null : ref.get();
        if (zipFile != null) {
          return zipFile;
        }

        zipFile = createZipFile(path);
        zipFileSoftReference = new SoftReference<>(zipFile);
        return zipFile;
      }
    }
    return createZipFile(path);
  }

  protected @NotNull ZipFile createZipFile(@NotNull Path path) throws IOException {
    // ZipFile's native implementation (ZipFile.c, zip_util.c) has path -> file descriptor cache
    return new ZipFile(path.toFile());
  }

  protected final void releaseZipFile(@NotNull ZipFile zipFile) throws IOException {
    // Closing of zip file when configuration.canLockJars=true happens in ZipFile.finalize
    if (!configuration.lockJars) {
      zipFile.close();
    }
  }

  @Override
  public final String toString() {
    return "JarLoader [" + path + "]";
  }
}