// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import com.intellij.openapi.diagnostic.Logger;
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

  private final boolean myDark;
  private final IconPathPatcher @NotNull [] myPatchers;
  private final @Nullable ImageFilter myFilter;

  public IconTransform(boolean dark, IconPathPatcher @NotNull [] patchers, @Nullable ImageFilter filter) {
    myDark = dark;
    myPatchers = patchers;
    myFilter = filter;
  }

  public boolean isDark() {
    return myDark;
  }

  public @Nullable ImageFilter getFilter() {
    return myFilter;
  }

  @NotNull
  public IconTransform withPathPatcher(@NotNull IconPathPatcher patcher) {
    return new IconTransform(myDark, ArrayUtil.append(myPatchers, patcher), myFilter);
  }

  @NotNull
  public IconTransform withoutPathPatcher(@NotNull IconPathPatcher patcher) {
    IconPathPatcher[] newPatchers = ArrayUtil.remove(myPatchers, patcher);
    return newPatchers == myPatchers ? this : new IconTransform(myDark, newPatchers, myFilter);
  }

  @NotNull
  public IconTransform withFilter(ImageFilter filter) {
    return filter == myFilter ? this : new IconTransform(myDark, myPatchers, filter);
  }

  @NotNull
  public IconTransform withDark(boolean dark) {
    return dark == myDark ? this : new IconTransform(dark, myPatchers, myFilter);
  }

  public @Nullable Pair<String, ClassLoader> patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
    for (IconPathPatcher patcher : myPatchers) {
      String newPath = patcher.patchPath(path, classLoader);
      if (newPath == null) {
        continue;
      }

      LOG.debug("replace '" + path + "' with '" + newPath + "'");
      ClassLoader contextClassLoader = patcher.getContextClassLoader(path, classLoader);
      if (contextClassLoader == null) {
        //noinspection deprecation
        Class<?> contextClass = patcher.getContextClass(path);
        if (contextClass != null) {
          contextClassLoader = contextClass.getClassLoader();
        }
      }
      return new Pair<>(newPath, contextClassLoader);
    }
    return null;
  }

  public @NotNull IconTransform copy() {
    return new IconTransform(myDark, myPatchers, myFilter);
  }
}
