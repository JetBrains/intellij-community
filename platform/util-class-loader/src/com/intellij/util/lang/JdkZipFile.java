// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.lang.fastutil.StrippedIntOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@ApiStatus.Internal
// ZipFile's native implementation (ZipFile.c, zip_util.c) has path -> file descriptor cache
public final class JdkZipFile implements ZipFileImpl {
  private volatile SoftReference<ZipFile> zipFileSoftReference;
  private final boolean lockJars;
  private final File file;
  private final boolean isSecureLoader;

  private static final Object lock = new Object();

  public JdkZipFile(@NotNull Path path, boolean lockJars, boolean isSecureLoader) {
    this.lockJars = lockJars;
    this.file = path.toFile();
    this.isSecureLoader = isSecureLoader;
  }

  @NotNull ZipFile getZipFile() throws IOException {
    // This code is executed at least 100K times (O(number of classes needed to load)) and it takes considerable time to open ZipFile's
    // such number of times so we store reference to ZipFile if we allowed to lock the file (assume it isn't changed)
    if (!lockJars) {
      return createZipFile(file);
    }

    SoftReference<ZipFile> ref = zipFileSoftReference;
    ZipFile zipFile = ref == null ? null : ref.get();
    if (zipFile != null) {
      return zipFile;
    }

    synchronized (lock) {
      ref = zipFileSoftReference;
      zipFile = ref == null ? null : ref.get();
      if (zipFile != null) {
        return zipFile;
      }

      zipFile = createZipFile(file);
      zipFileSoftReference = new SoftReference<>(zipFile);
    }
    return zipFile;
  }

  private ZipFile createZipFile(@NotNull File file) throws IOException {
    return isSecureLoader ? new JarFile(file) : new ZipFile(file);
  }

  @Override
  public void close() throws IOException {
    SoftReference<ZipFile> ref = zipFileSoftReference;
    ZipFile zipFile = ref == null ? null : ref.get();
    if (zipFile != null) {
      zipFileSoftReference = null;
      zipFile.close();
    }
  }

  @Override
  public @Nullable Resource getResource(@NotNull String name, @NotNull JarLoader jarLoader) throws IOException {
    try {
      ZipEntry entry = getZipFile().getEntry(name);
      if (entry == null) {
        return null;
      }
      return jarLoader.instantiateResource(entry);
    }
    finally {
      if (!lockJars) {
        close();
      }
    }
  }

  @Override
  public @Nullable JarMemoryLoader preload(@NotNull Path basePath, @Nullable JarLoader attributesProvider) throws IOException {
    ZipFile zipFile = getZipFile();
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    if (!entries.hasMoreElements()) {
      return null;
    }

    ZipEntry sizeEntry = entries.nextElement();
    if (sizeEntry == null || !sizeEntry.getName().equals(JarMemoryLoader.SIZE_ENTRY)) {
      return null;
    }

    byte[] bytes = Resource.loadBytes(zipFile.getInputStream(sizeEntry), 2);
    int size = ((bytes[1] & 0xFF) << 8) + (bytes[0] & 0xFF);

    Map<String, Resource> map = new HashMap<>();
    URL baseUrl = basePath.toUri().toURL();
    for (int i = 0; i < size && entries.hasMoreElements(); i++) {
      ZipEntry entry = entries.nextElement();
      MemoryResource resource =
        MemoryResource.load(baseUrl, zipFile, entry, attributesProvider == null ? null : attributesProvider.getAttributes());
      map.put(entry.getName(), resource);
    }
    return new JarMemoryLoader(map);
  }

  @Override
  public @Nullable Attributes loadManifestAttributes() throws IOException {
    ZipFile zipFile = getZipFile();
    ZipEntry entry = zipFile.getEntry(JarFile.MANIFEST_NAME);
    if (entry == null) {
      return null;
    }

    try (InputStream stream = zipFile.getInputStream(entry)) {
      return new Manifest(stream).getMainAttributes();
    }
    catch (Exception ignored) {
    }
    return null;
  }

  @Override
  public @NotNull ClasspathCache.LoaderData buildClassPathCacheData() throws IOException {
    try {
      ClasspathCache.LoaderDataBuilder loaderDataBuilder = new ClasspathCache.LoaderDataBuilder();
      Enumeration<? extends ZipEntry> entries = getZipFile().entries();
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
      if (!lockJars) {
        close();
      }
    }
  }

  @Override
  public @NotNull StrippedIntOpenHashSet buildPackageHashes() throws IOException {
    try {
      Enumeration<? extends ZipEntry> entries = getZipFile().entries();
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
      if (!lockJars) {
        close();
      }
    }
  }
}
