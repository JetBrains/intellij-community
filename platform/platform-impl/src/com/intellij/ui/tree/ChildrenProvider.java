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

import javax.swing.tree.TreeModel;
import java.util.List;

/**
 * This is an extension for the {@link TreeModel} which is supported by {@link AsyncTreeModel}.
 * It is intended to simplify implementing of a couple corresponding methods in a model.
 *
 * @see TreeModel#getChildCount(Object)
 * @see TreeModel#getChild(Object, int)
 *
 * @author Sergey.Malenkov
 */
public interface ChildrenProvider<T> {
  /**
   * @param parent a tree node
   * @return all children of the specified parent node
   */
  @NotNull
  List<T> getChildren(Object parent);
}
