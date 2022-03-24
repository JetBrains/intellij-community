// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

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

final class JarLoader implements Loader {
  public enum Attribute {
    SPEC_TITLE, SPEC_VERSION, SPEC_VENDOR, CLASS_PATH, IMPL_TITLE, IMPL_VERSION, IMPL_VENDOR
  }

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

  final ClassPath configuration;
  final URL url;
  final ResourceFile zipFile;
  private final Path path;

  JarLoader(@NotNull Path path, @NotNull ClassPath configuration, @NotNull ResourceFile zipFile) throws IOException {
    this.path = path;

    this.configuration = configuration;
    this.zipFile = zipFile;
    url = new URL("jar", "", -1, fileToUri(path) + "!/");
  }

  @Override
  public Path getPath() {
    return path;
  }

  @Override
  public boolean containsName(String name) {
    return true;
  }

  @Override
  public @Nullable Class<?> findClass(String fileName, String className, ClassPath.ClassDataConsumer classConsumer) throws IOException {
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

  static @NotNull Map<JarLoader.Attribute, String> getAttributes(@NotNull Attributes attributes) {
    if (attributes.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<JarLoader.Attribute, String> map = null;
    for (Map.Entry<JarLoader.Attribute, Attributes.Name> p : PACKAGE_FIELDS) {
      String value = attributes.getValue(p.getValue());
      if (value != null) {
        if (map == null) {
          map = new EnumMap<>(JarLoader.Attribute.class);
        }
        map.put(p.getKey(), value);
      }
    }
    return map == null ? Collections.emptyMap() : map;
  }

  @Override
  public @Nullable Resource getResource(@NotNull String name) {
    try {
      return zipFile.getResource(name, this);
    }
    catch (IOException e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public void processResources(@NotNull String dir,
                               @NotNull Predicate<? super String> fileNameFilter,
                               @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException {
    zipFile.processResources(dir, fileNameFilter, consumer);
  }

  @Override
  public String toString() {
    return "JarLoader(path=" + path + ")";
  }
}