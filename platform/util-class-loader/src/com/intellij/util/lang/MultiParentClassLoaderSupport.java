// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Deque;

/**
 * Interface for classloaders that support multi-parent hierarchy traversal.
 * Allows recursive collection of parent classloaders without coupling to concrete implementation.
 */
@ApiStatus.Internal
public interface MultiParentClassLoaderSupport {
  /**
   * Collects direct parent classloaders into the provided queue.
   * Used for recursive traversal of multi-parent classloader hierarchy.
   *
   * @param queue the queue to add parent classloaders to
   */
  void collectDirectParents(@NotNull Deque<ClassLoader> queue);
}
