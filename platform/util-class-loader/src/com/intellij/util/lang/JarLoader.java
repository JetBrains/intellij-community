// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.Attributes;

public class JarLoader extends Loader {
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
  private final SoftReference<JarMemoryLoader> memoryLoader;
  protected final ResourceFile zipFile;
  private volatile Map<Resource.Attribute, String> attributes;
  private volatile String classPathManifestAttribute;

  JarLoader(@NotNull Path file, @NotNull ClassPath configuration, @NotNull ResourceFile zipFile) throws IOException {
    super(file);

    this.configuration = configuration;
    this.zipFile = zipFile;
    this.url = new URL("jar", "", -1, fileToUri(file) + "!/");

    SoftReference<JarMemoryLoader> memoryLoader = null;
    if (configuration.preloadJarContents) {
      // IOException from opening is propagated to caller if zip file isn't valid
      try {
        JarMemoryLoader loader = zipFile.preload(path, this);
        if (loader != null) {
          memoryLoader = new SoftReference<>(loader);
        }
      }
      finally {
        releaseZipFile(zipFile);
      }
    }
    this.memoryLoader = memoryLoader;
  }

  // Path.toUri is broken — do not use it
  public static @NotNull URI fileToUri(@NotNull Path file) {
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

  final @Nullable String getClassPathManifestAttribute() throws IOException {
    loadManifestAttributes(zipFile);
    String result = classPathManifestAttribute;
    return result == NULL_STRING ? null : result;
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

  public final Map<Resource.Attribute, String> loadManifestAttributes(@NotNull ResourceFile resourceFile) throws IOException {
    Map<Resource.Attribute, String> result = attributes;
    if (result != null) {
      return result;
    }

    synchronized (this) {
      result = attributes;
      if (result != null) {
        return result;
      }

      Attributes manifestAttributes = configuration.getManifestData(path);
      if (manifestAttributes == null) {
        manifestAttributes = resourceFile.loadManifestAttributes();
        if (manifestAttributes == null) {
          manifestAttributes = new Attributes(0);
        }
        configuration.cacheManifestData(path, manifestAttributes);
      }

      result = getAttributes(manifestAttributes);
      attributes = result;
      Object attribute = manifestAttributes.get(Attributes.Name.CLASS_PATH);
      classPathManifestAttribute = attribute instanceof String ? (String)attribute : NULL_STRING;
    }
    return result;
  }

  @Override
  public final @NotNull ClasspathCache.IndexRegistrar buildData() throws IOException {
    return zipFile.buildClassPathCacheData();
  }

  @Override
  final @Nullable Resource getResource(@NotNull String name) {
    JarMemoryLoader loader = memoryLoader == null ? null : memoryLoader.get();
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

  protected final void error(@NotNull String message, @NotNull Throwable t) {
    if (configuration.errorOnMissingJar) {
      LoggerRt.getInstance(JarLoader.class).error(message, t);
    }
    else {
      LoggerRt.getInstance(JarLoader.class).warn(message, t);
    }
  }

  protected final void releaseZipFile(@NotNull ResourceFile zipFile) throws IOException {
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