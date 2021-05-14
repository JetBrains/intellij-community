// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
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
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@ApiStatus.Internal
// ZipFile's native implementation (ZipFile.c, zip_util.c) has path -> file descriptor cache
public final class JdkZipResourceFile implements ResourceFile {
  private final SoftReference<JarMemoryLoader> memoryLoader;

  private volatile SoftReference<ZipFile> zipFileSoftReference;
  private final boolean lockJars;
  private final File file;
  private final boolean isSecureLoader;

  private static final Object lock = new Object();

  public JdkZipResourceFile(@NotNull Path path, boolean lockJars, boolean preloadJarContents, boolean isSecureLoader) throws IOException {
    this.lockJars = lockJars;
    this.file = path.toFile();
    this.isSecureLoader = isSecureLoader;

    SoftReference<JarMemoryLoader> memoryLoader = null;
    if (preloadJarContents) {
      // IOException from opening is propagated to caller if zip file isn't valid
      try {
        JarMemoryLoader loader = preload(path);
        if (loader != null) {
          memoryLoader = new SoftReference<>(loader);
        }
      }
      finally {
        if (!lockJars) {
          close();
        }
      }
    }
    this.memoryLoader = memoryLoader;
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

  public void close() throws IOException {
    SoftReference<ZipFile> ref = zipFileSoftReference;
    ZipFile zipFile = ref == null ? null : ref.get();
    if (zipFile != null) {
      zipFileSoftReference = null;
      zipFile.close();
    }
  }

  @Override
  public @Nullable Class<?> findClass(@NotNull String fileName, String className, JarLoader jarLoader, ClassPath.ClassDataConsumer classConsumer)
    throws IOException {
    JarMemoryLoader memoryLoader = this.memoryLoader == null ? null : this.memoryLoader.get();
    if (memoryLoader != null) {
      byte[] data = memoryLoader.getBytes(fileName);
      if (data != null) {
        return classConsumer.consumeClassData(className, data, jarLoader, null);
      }
    }

    ZipFile zipFile = getZipFile();
    ZipEntry entry = zipFile.getEntry(fileName);
    if (entry == null) {
      return null;
    }

    byte[] bytes;
    try (InputStream stream = zipFile.getInputStream(entry)) {
      bytes = loadBytes(stream, (int)entry.getSize());
    }
    finally {
      if (!lockJars) {
        close();
      }
    }

    ProtectionDomain protectionDomain;
    if (jarLoader instanceof SecureJarLoader) {
      protectionDomain = ((SecureJarLoader)jarLoader).getProtectionDomain((JarEntry)entry, new URL(jarLoader.url, entry.getName()));
    }
    else {
      protectionDomain = null;
    }
    return classConsumer.consumeClassData(className, bytes, jarLoader, protectionDomain);
  }

  @Override
  public void processResources(@NotNull String dir,
                               @NotNull Predicate<? super String> filter,
                               @NotNull BiConsumer<? super String, ? super InputStream> consumer) {
  }

  @Override
  public @Nullable Resource getResource(@NotNull String name, @NotNull JarLoader jarLoader) throws IOException {
    JarMemoryLoader loader = memoryLoader == null ? null : memoryLoader.get();
    if (loader != null) {
      Resource resource = loader.getResource(name);
      if (resource != null) {
        return resource;
      }
    }

    try {
      ZipEntry entry = getZipFile().getEntry(name);
      if (entry == null) {
        return null;
      }
      if (isSecureLoader) {
        return new SecureJarResource(jarLoader.url, (JarEntry)entry, (SecureJarLoader)jarLoader);
      }
      else {
        return new ZipFileResource(jarLoader.url, entry, this);
      }
    }
    finally {
      if (!lockJars) {
        close();
      }
    }
  }

  public @Nullable JarMemoryLoader preload(@NotNull Path basePath) throws IOException {
    ZipFile zipFile = getZipFile();
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    if (!entries.hasMoreElements()) {
      return null;
    }

    ZipEntry sizeEntry = entries.nextElement();
    if (sizeEntry == null || !sizeEntry.getName().equals(JarMemoryLoader.SIZE_ENTRY)) {
      return null;
    }

    byte[] bytes = loadBytes(zipFile.getInputStream(sizeEntry), 2);
    int size = ((bytes[1] & 0xFF) << 8) + (bytes[0] & 0xFF);

    Object[] table = new Object[((size * 4) + 1) & ~1];
    String baseUrl = JarLoader.fileToUri(basePath).toString();
    for (int i = 0; i < size && entries.hasMoreElements(); i++) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      int index = JarMemoryLoader.probePlain(name, table);
      if (index >= 0) {
        throw new IllegalArgumentException("duplicate name: " + name);
      }
      else {
        byte[] content;
        try (InputStream stream = zipFile.getInputStream(entry)) {
          content = loadBytes(stream, (int)entry.getSize());
        }

        int dest = -(index + 1);
        table[dest] = name;
        table[dest + 1] = new MemoryResource(baseUrl, content, name);
      }
    }
    return new JarMemoryLoader(table);
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
  public @NotNull ClasspathCache.IndexRegistrar buildClassPathCacheData() throws IOException {
    try {
      ClasspathCache.LoaderDataBuilder builder = new ClasspathCache.LoaderDataBuilder(true);
      Enumeration<? extends ZipEntry> entries = getZipFile().entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.endsWith(ClassPath.CLASS_EXTENSION)) {
          builder.addClassPackageFromName(name);
          builder.andClassName(name);
        }
        else {
          builder.addResourcePackageFromName(name);
          builder.addResourceName(name, name.endsWith("/") ? name.length() - 1 : name.length());
        }
      }
      return builder;
    }
    finally {
      if (!lockJars) {
        close();
      }
    }
  }

  private static class ZipFileResource implements Resource {
    protected final URL baseUrl;
    private URL url;
    protected final ZipEntry entry;
    protected final JdkZipResourceFile file;

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
      try (InputStream stream = file.getZipFile().getInputStream(entry)) {
        return loadBytes(stream, (int)entry.getSize());
      }
      finally {
        if (!file.lockJars) {
          file.close();
        }
      }
    }
  }

  private static final class SecureJarResource extends JdkZipResourceFile.ZipFileResource {
    SecureJarResource(@NotNull URL baseUrl, @NotNull JarEntry entry, @NotNull SecureJarLoader jarLoader) {
      super(baseUrl, entry, (JdkZipResourceFile)jarLoader.zipFile);
    }

    @Override
    public byte @NotNull [] getBytes() throws IOException {
      try (InputStream stream = file.getZipFile().getInputStream(entry)) {
        return loadBytes(stream, (int)entry.getSize());
      }
      finally {
        if (!file.lockJars) {
          file.close();
        }
      }
    }
  }
}
