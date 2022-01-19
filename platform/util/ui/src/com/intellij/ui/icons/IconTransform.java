// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.ImageFilter;

/**
 * Immutable representation of a global transformation applied to all icons
 */
public final class IconTransform {
  private static final Logger LOG = Logger.getInstance(IconTransform.class);

  private final boolean dark;
  private final IconPathPatcher @NotNull [] patchers;
  private final @Nullable ImageFilter filter;

  public IconTransform(boolean dark, IconPathPatcher @NotNull [] patchers, @Nullable ImageFilter filter) {
    this.dark = dark;
    this.patchers = patchers;
    this.filter = filter;
  }

  public boolean isDark() {
    return dark;
  }

  public @Nullable ImageFilter getFilter() {
    return filter;
  }

  public @NotNull IconTransform withPathPatcher(@NotNull IconPathPatcher patcher) {
    return new IconTransform(dark, ArrayUtil.append(patchers, patcher), filter);
  }

  public @NotNull IconTransform withoutPathPatcher(@NotNull IconPathPatcher patcher) {
    IconPathPatcher[] newPatchers = ArrayUtil.remove(patchers, patcher);
    return newPatchers == patchers ? this : new IconTransform(dark, newPatchers, filter);
  }

  public @NotNull IconTransform withFilter(ImageFilter filter) {
    return filter == this.filter ? this : new IconTransform(dark, patchers, filter);
  }

  public @NotNull IconTransform withDark(boolean dark) {
    return dark == this.dark ? this : new IconTransform(dark, patchers, filter);
  }

  public @Nullable Pair<String, ClassLoader> patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
    String pathWithLeadingSlash = path.charAt(0) == '/' ? path : ('/' + path);
    for (IconPathPatcher patcher : patchers) {
      String newPath;
      try {
        newPath = patcher.patchPath(pathWithLeadingSlash, classLoader);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(patcher + " cannot patch icon path", e);
        continue;
      }

      if (newPath == null) {
        continue;
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("replace '" + path + "' with '" + newPath + "'");
      }

      ClassLoader contextClassLoader = patcher.getContextClassLoader(pathWithLeadingSlash, classLoader);
      if (contextClassLoader == null) {
        //noinspection deprecation
        Class<?> contextClass = patcher.getContextClass(pathWithLeadingSlash);
        if (contextClass != null) {
          contextClassLoader = contextClass.getClassLoader();
        }
      }
      return new Pair<>(newPath, contextClassLoader);
    }
    return null;
  }

  public @NotNull IconTransform copy() {
    return new IconTransform(dark, patchers, filter);
  }
}
