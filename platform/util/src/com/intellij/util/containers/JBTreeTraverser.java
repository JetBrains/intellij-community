/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.util.Function;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NotNull;

public class JBTreeTraverser<T> extends FilteredTraverserBase<T, JBTreeTraverser<T>> {

  @NotNull
  public static <T> JBTreeTraverser<T> from(@NotNull Function<T, ? extends Iterable<? extends T>> treeStructure) {
    return new JBTreeTraverser<T>(treeStructure);
  }

  @NotNull
  public static <T> JBTreeTraverser<T> of(@NotNull Function<T, T[]> treeStructure) {
    return new JBTreeTraverser<T>(Functions.compose(treeStructure, Functions.<T>wrapArray()));
  }

  public JBTreeTraverser(Function<T, ? extends Iterable<? extends T>> treeStructure) {
    super(null, treeStructure);
  }

  protected JBTreeTraverser(Meta<T> meta, Function<T, ? extends Iterable<? extends T>> treeStructure) {
    super(meta, treeStructure);
  }

  @NotNull
  @Override
  protected JBTreeTraverser<T> newInstance(Meta<T> meta) {
    return new JBTreeTraverser<T>(meta, getTree());
  }
}