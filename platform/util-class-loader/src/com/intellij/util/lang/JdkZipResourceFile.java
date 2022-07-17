// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// ZipFile's native implementation (ZipFile.c, zip_util.c) has path -> file descriptor cache
final class JdkZipResourceFile implements ResourceFile {
  private volatile SoftReference<ZipFile> zipFileSoftReference;
  private final boolean lockJars;
  private final File file;

  private static final Object lock = new Object();

  JdkZipResourceFile(@NotNull Path path, boolean lockJars) {
    this.lockJars = lockJars;
    this.file = path.toFile();
  }

  @SuppressWarnings("DuplicatedCode")
  private static byte @NotNull [] loadBytes(@NotNull InputStream stream, int length) throws IOException {
    byte[] bytes = new byte[length];
    int count = 0;
    while (count < length) {
      int n = stream.read(bytes, count, length - count);
      if (n <= 0) break;
      count += n;
    }
    return bytes;
  }

  @NotNull ZipFile getZipFile() throws IOException {
    // This code is executed at least 100K times (O(number of classes needed to load)), and it takes considerable time to open ZipFile's
    // such number of times, so we store reference to ZipFile if we allowed to lock the file (assume it isn't changed)
    if (!lockJars) {
      return new ZipFile(file);
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

      zipFile = new ZipFile(file);
      zipFileSoftReference = new SoftReference<>(zipFile);
    }
    return zipFile;
  }

  @Override
  public @Nullable Class<?> findClass(@NotNull String fileName, String className, JarLoader jarLoader, ClassPath.ClassDataConsumer classConsumer)
    throws IOException {
    ZipFile zipFile = getZipFile();
    try {
      ZipEntry entry = zipFile.getEntry(fileName);
      if (entry == null) {
        return null;
      }

      byte[] bytes;
      try (InputStream stream = zipFile.getInputStream(entry)) {
        bytes = loadBytes(stream, (int)entry.getSize());
      }
      return classConsumer.consumeClassData(className, bytes, jarLoader);
    }
    finally {
      if (!lockJars) {
        zipFile.close();
      }
    }
  }

  @Override
  public void processResources(@NotNull String dir,
                               @NotNull Predicate<? super String> filter,
                               @NotNull BiConsumer<? super String, ? super InputStream> consumer) {
  }

  @Override
  public @Nullable Resource getResource(@NotNull String name, @NotNull JarLoader jarLoader) throws IOException {
    ZipFile zipFile = getZipFile();
    try {
      ZipEntry entry = zipFile.getEntry(name);
      if (entry == null) {
        return null;
      }
      return new ZipFileResource(jarLoader.url, entry, this);
    }
    finally {
      if (!lockJars) {
        zipFile.close();
      }
    }
  }

  @Override
  public @Nullable Attributes loadManifestAttributes() throws IOException {
    ZipFile zipFile = getZipFile();
    try {
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
    finally {
      if (!lockJars){
        zipFile.close();
      }
    }
  }

  @Override
  public @NotNull ClasspathCache.IndexRegistrar buildClassPathCacheData() throws IOException {
    ZipFile zipFile = getZipFile();
    try {
      ClasspathCache.LoaderDataBuilder builder = new ClasspathCache.LoaderDataBuilder();
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        builder.addPackageFromName(entries.nextElement().getName());
      }
      return builder;
    }
    finally {
      if (!lockJars) {
        zipFile.close();
      }
    }
  }

  private static final class ZipFileResource implements Resource {
    private final URL baseUrl;
    private URL url;
    private final ZipEntry entry;
    private final JdkZipResourceFile file;

    private ZipFileResource(@NotNull URL baseUrl, @NotNull ZipEntry entry, @NotNull JdkZipResourceFile file) {
      this.baseUrl = baseUrl;
      this.entry = entry;
      this.file = file;
    }

    @Override
    public String toString() {
      return url.toString();
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
      ZipFile zipFile = file.getZipFile();
      try (InputStream stream = zipFile.getInputStream(entry)) {
        return loadBytes(stream, (int)entry.getSize());
      }
      finally {
        if (!file.lockJars) {
          zipFile.close();
        }
      }
    }
  }
}
