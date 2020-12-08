// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.util.lang.fastutil.StrippedIntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.zip.ZipEntry;

class JarLoader extends Loader {
  private static final List<Map.Entry<Resource.Attribute, Attributes.Name>> PACKAGE_FIELDS = Arrays.asList(
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_TITLE, Attributes.Name.SPECIFICATION_TITLE),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_VERSION, Attributes.Name.SPECIFICATION_VERSION),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.SPEC_VENDOR, Attributes.Name.SPECIFICATION_VENDOR),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_TITLE, Attributes.Name.IMPLEMENTATION_TITLE),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_VERSION, Attributes.Name.IMPLEMENTATION_VERSION),
    new AbstractMap.SimpleImmutableEntry<>(Resource.Attribute.IMPL_VENDOR, Attributes.Name.IMPLEMENTATION_VENDOR));

  private static final String NULL_STRING = "<null>";

  protected final ClassPath configuration;
  protected final URL url;
  private SoftReference<JarMemoryLoader> memoryLoader;
  protected final ZipFileImpl zipFile;
  private volatile Map<Resource.Attribute, String> attributes;
  private volatile String classPathManifestAttribute;

  private final AtomicInteger myNumberOfRequests = new AtomicInteger();
  private volatile StrippedIntOpenHashSet myPackageHashesInside;

  JarLoader(@NotNull Path file, @NotNull ClassPath configuration) throws IOException {
    this(file, configuration, new JdkZipFile(file, configuration.lockJars, false));
  }

  JarLoader(@NotNull Path file, @NotNull ClassPath configuration, @NotNull ZipFileImpl zipFile) throws IOException {
    super(file);

    this.configuration = configuration;
    this.zipFile = zipFile;
    this.url = new URL("jar", "", -1, fileToUri(file) + "!/");

    if (!configuration.lazyClassloadingCaches) {
      // IOException from opening is propagated to caller if zip file isn't valid,
      try {
        if (configuration.preloadJarContents) {
          JarMemoryLoader loader = zipFile.preload(path, this);
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

  // Path.toUri is broken — do not use it
  private static @NotNull URI fileToUri(@NotNull Path file) {
    String path = file.toString().replace(File.separatorChar, '/');
    if (!path.startsWith("/")) {
      path = '/' + path;
    }
    else if (path.startsWith("//")) {
      path = "//" + path;
    }

    try {
      return new URI("file", null, path, null);
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(path, e);
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

  private static @Nullable Map<Resource.Attribute, String> getAttributes(@NotNull Attributes attributes) {
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

        try {
          Attributes manifestAttributes = configuration.getManifestData(path);
          if (manifestAttributes == null) {
            manifestAttributes = zipFile.loadManifestAttributes();
            if (manifestAttributes == null) {
              manifestAttributes = new Attributes(0);
            }
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

  @Override
  public final @NotNull ClasspathCache.LoaderData buildData() throws IOException {
    return zipFile.buildClassPathCacheData();
  }

  @Override
  @Nullable Resource getResource(@NotNull String name) {
    if (configuration.lazyClassloadingCaches) {
      int numberOfHits = myNumberOfRequests.incrementAndGet();
      StrippedIntOpenHashSet packagesInside = myPackageHashesInside;

      if (numberOfHits > ClasspathCache.NUMBER_OF_ACCESSES_FOR_LAZY_CACHING && packagesInside == null) {
        try {
          packagesInside = zipFile.buildPackageHashes();
        }
        catch (IOException e) {
          packagesInside = new StrippedIntOpenHashSet(0);
        }
        myPackageHashesInside = packagesInside;
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
      return zipFile.getResource(name, this);
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
      JdkZipFile file = (JdkZipFile)zipFile;
      try (InputStream stream = file.getZipFile().getInputStream(entry)) {
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

  protected final void releaseZipFile(@NotNull ZipFileImpl zipFile) throws IOException {
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