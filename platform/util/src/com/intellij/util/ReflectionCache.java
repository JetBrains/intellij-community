// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 * @deprecated Contrary to the name, this class doesn't do any caching. So the usages may be safely dropped in favor of plain reflection calls.
 * <p>
 * Consider caching higher-level things, if you see reflection in your snapshots.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public final class ReflectionCache {
  /**
   * @deprecated doesn't cache
   */
  @Deprecated
  public static boolean isAssignable(@NotNull Class ancestor, Class descendant) {
    DeprecatedMethodException.report("Use 'ancestor.isAssignableFrom(descendant)' instead");
    return ancestor == descendant || ancestor.isAssignableFrom(descendant);
  }
}
