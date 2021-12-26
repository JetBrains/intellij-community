// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.ui.icons.IconTransform;
import com.intellij.ui.icons.ImageDataLoader;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.ImageFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@ApiStatus.Internal
public final class ImageDataByUrlLoader implements ImageDataLoader {
  private static final URL UNRESOLVED_URL;

  static {
    try {
      UNRESOLVED_URL = new URL("file:///unresolved");
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private final @Nullable Class<?> ownerClass;
  private final @Nullable ClassLoader classLoader;
  private final @Nullable String overriddenPath;
  private final @NotNull IconLoader.HandleNotFound handleNotFound;

  private volatile URL url;

  private final boolean useCacheOnLoad;

  ImageDataByUrlLoader(@NotNull URL url, @Nullable ClassLoader classLoader, boolean useCacheOnLoad) {
    ownerClass = null;
    overriddenPath = null;
    this.classLoader = classLoader;
    this.url = url;
    handleNotFound = IconLoader.HandleNotFound.IGNORE;
    this.useCacheOnLoad = useCacheOnLoad;
  }

  public ImageDataByUrlLoader(@NotNull URL url, @NotNull String path, @Nullable ClassLoader classLoader, boolean useCacheOnLoad) {
    ownerClass = null;
    overriddenPath = path;
    this.classLoader = classLoader;
    this.url = url;
    handleNotFound = IconLoader.HandleNotFound.IGNORE;
    this.useCacheOnLoad = useCacheOnLoad;
  }

  ImageDataByUrlLoader(@NotNull String path,
                       @Nullable Class<?> clazz,
                       @Nullable ClassLoader classLoader,
                       @Nullable IconLoader.HandleNotFound handleNotFound,
                       boolean useCacheOnLoad) {
    overriddenPath = path;
    ownerClass = clazz;
    this.classLoader = classLoader;
    if (handleNotFound == null) {
      handleNotFound = IconLoader.STRICT_LOCAL.get() ? IconLoader.HandleNotFound.THROW_EXCEPTION : IconLoader.HandleNotFound.IGNORE;
    }
    this.handleNotFound = handleNotFound;
    this.useCacheOnLoad = useCacheOnLoad;
    url = UNRESOLVED_URL;
  }

  @Override
  public @Nullable Image loadImage(@NotNull List<? extends ImageFilter> filters, @NotNull ScaleContext scaleContext, boolean isDark) {
    int flags = ImageLoader.USE_SVG | ImageLoader.ALLOW_FLOAT_SCALING;
    if (useCacheOnLoad) {
      flags |= ImageLoader.USE_CACHE;
    }
    if (isDark) {
      flags |= ImageLoader.USE_DARK;
    }

    String path = overriddenPath;
    if (path == null || (ownerClass == null && (classLoader == null || path.charAt(0) != '/'))) {
      URL url = getURL();
      if (url == null) {
        return null;
      }
      path = url.toString();
    }
    return ImageLoader.loadImage(path, filters, ownerClass, classLoader, flags, scaleContext, !path.endsWith(".svg"));
  }

  /**
   * Resolves the URL if it's not yet resolved.
   */
  public void resolve() {
    getURL();
  }

  @Override
  public @Nullable URL getURL() {
    URL result = this.url;
    if (result == UNRESOLVED_URL) {
      result = null;
      try {
        result = IconLoader.doResolve(overriddenPath, classLoader, ownerClass, handleNotFound);
      }
      finally {
        this.url = result;
      }
    }
    return result;
  }

  @Override
  public @Nullable ImageDataLoader patch(@NotNull String originalPath, @NotNull IconTransform transform) {
    return IconLoader.createNewResolverIfNeeded(classLoader, originalPath, transform);
  }

  @Override
  public boolean isMyClassLoader(@NotNull ClassLoader classLoader) {
    return this.classLoader == classLoader;
  }

  @Override
  public String toString() {
    return "UrlResolver{" +
           "ownerClass=" + (ownerClass == null ? "null" : ownerClass.getName()) +
           ", classLoader=" + classLoader +
           ", overriddenPath='" + overriddenPath + '\'' +
           ", url=" + url +
           ", useCacheOnLoad=" + useCacheOnLoad +
           '}';
  }
}
