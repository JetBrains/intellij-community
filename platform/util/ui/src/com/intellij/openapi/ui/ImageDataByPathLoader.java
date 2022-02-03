// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ImageDataByUrlLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.IconTransform;
import com.intellij.ui.icons.ImageDataLoader;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.ImageFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("HardCodedStringLiteral")
@ApiStatus.Internal
public final class ImageDataByPathLoader implements ImageDataLoader {
  final String path;
  final ClassLoader classLoader;
  private final @Nullable ImageDataByPathLoader original;

  private ImageDataByPathLoader(@NotNull String path, @NotNull ClassLoader classLoader, @Nullable ImageDataByPathLoader original) {
    this.path = path;
    this.classLoader = classLoader;
    this.original = original;
  }

  // cache is not used - image data resolved using cache in any case.
  public static @Nullable Icon findIconFromThemePath(@NotNull @NonNls String originalPath, @NotNull ClassLoader originalClassLoader) {
    long startTime = StartUpMeasurer.getCurrentTimeIfEnabled();

    originalPath = normalizePath(originalPath);

    Pair<String, ClassLoader> patched = IconLoader.patchPath(originalPath, originalClassLoader);
    String path = patched == null ? originalPath : patched.first;
    ClassLoader classLoader = patched == null || patched.second == null ? originalClassLoader : patched.second;
    Icon icon;
    if (IconLoader.isReflectivePath(path)) {
      icon = IconLoader.getReflectiveIcon(path, classLoader);
    }
    else {
      ImageDataByPathLoader loader = new ImageDataByPathLoader(originalPath, originalClassLoader, null);
      return new IconLoader.CachedImageIcon(null, patched == null ? loader : new ImageDataByPathLoader(path, classLoader, loader), null,
                                            null);
    }

    if (startTime != -1) {
      IconLoadMeasurer.findIcon.end(startTime);
    }
    return icon;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ImageDataByPathLoader)) return false;

    ImageDataByPathLoader loader = (ImageDataByPathLoader)o;

    if (!path.equals(loader.path)) return false;
    if (!classLoader.equals(loader.classLoader)) return false;
    if (!Objects.equals(original, loader.original)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = path.hashCode();
    result = 31 * result + classLoader.hashCode();
    result = 31 * result + (original != null ? original.hashCode() : 0);
    return result;
  }

  private static @NotNull String normalizePath(String patchedPath) {
    return patchedPath.charAt(0) == '/' ? patchedPath.substring(1) : patchedPath;
  }

  @Override
  public @Nullable Image loadImage(@NotNull List<? extends ImageFilter> filters,
                                   @NotNull ScaleContext scaleContext,
                                   boolean isDark) {
    int flags = ImageLoader.ALLOW_FLOAT_SCALING | ImageLoader.USE_CACHE;
    if (isDark) {
      flags |= ImageLoader.USE_DARK;
    }
    return ImageLoader.loadImage(path, filters, null, classLoader, flags, scaleContext, !path.endsWith(".svg"));
  }

  @Override
  public @Nullable URL getURL() {
    return classLoader.getResource(path);
  }

  @Override
  public @Nullable ImageDataLoader patch(@NotNull String __, @NotNull IconTransform transform) {
    boolean isOriginal = original == null;
    return doPatch(isOriginal ? this : original, transform, isOriginal);
  }

  private static @Nullable ImageDataLoader doPatch(@NotNull ImageDataByPathLoader originalLoader,
                                                   @NotNull IconTransform transform,
                                                   boolean isOriginal) {
    Pair<String, ClassLoader> patched = transform.patchPath(originalLoader.path, originalLoader.classLoader);
    if (patched == null) {
      return isOriginal ? null : originalLoader;
    }
    ClassLoader classLoader = patched.second == null ? originalLoader.classLoader : patched.second;
    if (patched.first.startsWith("file:/")) {
      try {
        return new ImageDataByUrlLoader(new URL(patched.first), patched.first, classLoader, false);
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return new ImageDataByPathLoader(normalizePath(patched.first), classLoader, originalLoader);
    }
  }

  @Override
  public boolean isMyClassLoader(@NotNull ClassLoader classLoader) {
    return this.classLoader == classLoader || (original != null && original.classLoader == classLoader);
  }

  @Override
  public String toString() {
    return "ImageDataByPathLoader(" +
           ", classLoader=" + classLoader +
           ", path='" + path + '\'' +
           ", original='" + original + '\'' +
           ')';
  }
}
