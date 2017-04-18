/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.tree;

import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;

/**
 * @author Sergey.Malenkov
 */
public interface Identifiable {
  /**
   * Returns an unique identifier for the specified path if applicable.
   * This method is usually used to store a tree state on disposing,
   * so we cannot use invoker to calculate it later on a valid thread,
   * and an implementor must be ready to support any thread.
   *
   * @param path a tree path in the current tree model
   * @return an unique identifier for the specified path, or {@code null} if not applicable
   * @see Searchable#getTreePath
   */
  Object getUniqueID(@NotNull TreePath path);
}
