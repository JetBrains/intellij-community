// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.util.ArrayUtil;
import kotlin.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.ImageFilter;

/**
 * Immutable representation of a global transformation applied to all icons
 */
@ApiStatus.Internal
public final class IconTransform {
  private static final Logger LOG = Logger.getInstance(IconTransform.class);

  private final boolean dark;
  private final IconPathPatcher @NotNull [] patchers;
  private final IconPathPatcher @NotNull [] postPatchers;
  private final @Nullable ImageFilter filter;

  public IconTransform(boolean dark, IconPathPatcher @NotNull [] patchers, @Nullable ImageFilter filter) {
    this(dark, patchers, new IconPathPatcher[0], filter);
  }

  /**
   * Creates a new instance of IconTransform with the specified parameters.
   *
   * @param dark             true if the icon should be transformed for dark mode, false otherwise
   * @param patchers         an array of IconPathPatcher objects used to modify the icon path before transforming it
   * @param postPatchers     an array of IconPathPatcher objects used to modify the icon path before transforming it.
   *                         During icon path patching patchers are iterated first, and if neither of them worked out, postPatchers are iterated.
   * @param filter           the ImageFilter to apply to the transformed icon, or null if no filter should be applied
   */
  private IconTransform(boolean dark,
                       IconPathPatcher @NotNull [] patchers,
                       IconPathPatcher @NotNull [] postPatchers,
                       @Nullable ImageFilter filter) {
    this.dark = dark;
    this.patchers = patchers;
    this.postPatchers = postPatchers;
    this.filter = filter;
  }

  public boolean isDark() {
    return dark;
  }

  public @Nullable ImageFilter getFilter() {
    return filter;
  }

  public @NotNull IconTransform withPathPatcher(@NotNull IconPathPatcher patcher) {
    return new IconTransform(dark, ArrayUtil.append(patchers, patcher), postPatchers, filter);
  }

  @ApiStatus.Internal
  public @NotNull IconTransform withPostPathPatcher(@NotNull IconPathPatcher patcher) {
    return new IconTransform(dark, patchers, ArrayUtil.append(postPatchers, patcher), filter);
  }

  public @NotNull IconTransform withoutPathPatcher(@NotNull IconPathPatcher patcher) {
    IconPathPatcher[] newPatchers = ArrayUtil.remove(patchers, patcher);
    IconPathPatcher[] newLastPatchers = postPatchers;
    if (newPatchers == patchers) newLastPatchers = ArrayUtil.remove(postPatchers, patcher);

    return newPatchers == patchers && newLastPatchers == postPatchers ? this : new IconTransform(dark, newPatchers, postPatchers, filter);
  }

  public @NotNull IconTransform withFilter(ImageFilter filter) {
    return filter == this.filter ? this : new IconTransform(dark, patchers, postPatchers, filter);
  }

  public @NotNull IconTransform withDark(boolean dark) {
    return dark == this.dark ? this : new IconTransform(dark, patchers, postPatchers, filter);
  }

  public @Nullable Pair<String, ClassLoader> patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
    String pathWithLeadingSlash = path.charAt(0) == '/' ? path : ('/' + path);
    int length = patchers.length + postPatchers.length;
    for (int i = 0; i < length; i++) {
      IconPathPatcher patcher;
      if (i < patchers.length) patcher = patchers[i];
      else patcher = postPatchers[i - patchers.length];

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
    return new IconTransform(dark, patchers, postPatchers, filter);
  }
}
