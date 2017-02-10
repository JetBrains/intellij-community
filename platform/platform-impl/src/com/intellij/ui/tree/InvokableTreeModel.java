/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.tree.AbstractTreeModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;

/**
 * @author Sergey.Malenkov
 */
public abstract class InvokableTreeModel extends AbstractTreeModel implements Disposable {
  protected final Invoker invoker = createInvoker();

  /**
   * Creates a new invoker to process tree commands.
   * By default, these commands are processed
   * on a single background thread one after another.
   *
   * @return a new invoker for current tree model
   */
  @NotNull
  protected Invoker createInvoker() {
    return new Invoker.BackgroundQueue(this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
  }
}
