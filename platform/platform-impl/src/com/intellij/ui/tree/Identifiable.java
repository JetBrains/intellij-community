// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;

@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
@SuppressWarnings("MissingDeprecatedAnnotation")
public interface Identifiable {
  /**
   * Returns an unique identifier for the specified path if applicable.
   * This method is usually used to store a tree state on disposing,
   * so we cannot use invoker to calculate it later on a valid thread,
   * and an implementor must be ready to support any thread.
   *
   * @param path a tree path in the current tree model
   * @return an unique identifier for the specified path, or {@code null} if not applicable
   */
  Object getUniqueID(@NotNull TreePath path);
}
