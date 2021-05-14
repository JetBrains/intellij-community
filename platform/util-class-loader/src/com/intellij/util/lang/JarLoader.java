// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;

public class JarLoader extends Loader {
  @SuppressWarnings("unchecked")
  private static final Map.Entry<Attribute, Attributes.Name>[] PACKAGE_FIELDS = new Map.Entry[]{
    new AbstractMap.SimpleImmutableEntry<>(Attribute.SPEC_TITLE, Attributes.Name.SPECIFICATION_TITLE),
    new AbstractMap.SimpleImmutableEntry<>(Attribute.SPEC_VERSION, Attributes.Name.SPECIFICATION_VERSION),
    new AbstractMap.SimpleImmutableEntry<>(Attribute.SPEC_VENDOR, Attributes.Name.SPECIFICATION_VENDOR),
    new AbstractMap.SimpleImmutableEntry<>(Attribute.CLASS_PATH, Attributes.Name.CLASS_PATH),
    new AbstractMap.SimpleImmutableEntry<>(Attribute.IMPL_TITLE, Attributes.Name.IMPLEMENTATION_TITLE),
    new AbstractMap.SimpleImmutableEntry<>(Attribute.IMPL_VERSION, Attributes.Name.IMPLEMENTATION_VERSION),
    new AbstractMap.SimpleImmutableEntry<>(Attribute.IMPL_VENDOR, Attributes.Name.IMPLEMENTATION_VENDOR)
  };

  protected final ClassPath configuration;
  final URL url;
  protected final ResourceFile zipFile;
  private volatile Map<Loader.Attribute, String> attributes;

  JarLoader(@NotNull Path file, @NotNull ClassPath configuration, @NotNull ResourceFile zipFile) throws IOException {
    super(file);

    this.configuration = configuration;
    this.zipFile = zipFile;
    url = new URL("jar", "", -1, fileToUri(file) + "!/");
  }

  @Override
  public final Map<Attribute, String> getAttributes() throws IOException {
    return loadManifestAttributes(zipFile);
  }

  @Override
  final @Nullable Class<?> findClass(@NotNull String fileName, String className, @NotNull ClassPath.ClassDataConsumer classConsumer) throws IOException {
    return zipFile.findClass(fileName, className, this, classConsumer);
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
    return loadManifestAttributes(zipFile).get(Attribute.CLASS_PATH);
  }

  private static @NotNull Map<Loader.Attribute, String> getAttributes(@NotNull Attributes attributes) {
    if (attributes.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<Loader.Attribute, String> map = null;
    for (Map.Entry<Loader.Attribute, Attributes.Name> p : PACKAGE_FIELDS) {
      String value = attributes.getValue(p.getValue());
      if (value != null) {
        if (map == null) {
          map = new EnumMap<>(Loader.Attribute.class);
        }
        map.put(p.getKey(), value);
      }
    }
    return map == null ? Collections.emptyMap() : map;
  }

  private @NotNull Map<Loader.Attribute, String> loadManifestAttributes(@NotNull ResourceFile resourceFile) throws IOException {
    Map<Loader.Attribute, String> result = attributes;
    if (result != null) {
      return result;
    }

    synchronized (this) {
      result = attributes;
      if (result != null) {
        return result;
      }

      result = configuration.getManifestData(path);
      if (result == null) {
        Attributes manifestAttributes = resourceFile.loadManifestAttributes();
        result = manifestAttributes == null ? Collections.emptyMap() : getAttributes(manifestAttributes);
        configuration.cacheManifestData(path, result);
      }
      attributes = result;
    }
    return result;
  }

  @Override
  public final @NotNull ClasspathCache.IndexRegistrar buildData() throws IOException {
    return zipFile.buildClassPathCacheData();
  }

  @Override
  final @Nullable Resource getResource(@NotNull String name) {
    try {
      return zipFile.getResource(name, this);
    }
    catch (IOException e) {
      error("url: " + path, e);
      return null;
    }
  }

  @Override
  void processResources(@NotNull String dir,
                        @NotNull Predicate<? super String> fileNameFilter,
                        @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException {
    zipFile.processResources(dir, fileNameFilter, consumer);
  }

  protected final void error(@NotNull String message, @NotNull Throwable t) {
    LoggerRt logger = LoggerRt.getInstance(JarLoader.class);
    if (configuration.errorOnMissingJar) {
      logger.error(message, t);
    }
    else {
      logger.warn(message, t);
    }
  }

  @Override
  public final String toString() {
    return "JarLoader [" + path + "]";
  }
}