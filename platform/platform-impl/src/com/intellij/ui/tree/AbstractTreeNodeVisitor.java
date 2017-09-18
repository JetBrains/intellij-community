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

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class AbstractTreeNodeVisitor<T> implements TreeVisitor {
  protected static final Logger LOG = Logger.getInstance(AbstractTreeNodeVisitor.class);
  private final Supplier<T> supplier;
  private final Predicate<TreePath> predicate;

  public AbstractTreeNodeVisitor(Supplier<T> supplier, Predicate<TreePath> predicate) {
    this.supplier = supplier;
    this.predicate = predicate;
  }

  public AbstractTreeNodeVisitor(Supplier<T> supplier, Consumer<TreePath> consumer) {
    this(supplier, consumer == null ? null : path -> {
      consumer.accept(path);
      return false;
    });
  }

  @NotNull
  @Override
  public Action accept(@NotNull TreePath path) {
    if (LOG.isTraceEnabled()) LOG.debug("process ", path);
    T element = supplier == null ? null : supplier.get();
    if (element == null) return Action.SKIP_SIBLINGS;
    Object component = path.getLastPathComponent();
    if (component instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
      Object object = node.getUserObject();
      if (object instanceof AbstractTreeNode) {
        return accept(path, (AbstractTreeNode)object, element);
      }
      else {
        LOG.warn(object == null ? "no object" : "unexpected object " + object.getClass());
      }
    }
    else {
      LOG.warn(component == null ? "no component" : "unexpected component " + component.getClass());
    }
    return Action.SKIP_CHILDREN;
  }

  /**
   * @param path    a currently visited path
   * @param node    a node of a tree structure
   * @param element an element to find
   * @return an action that controls visiting a tree
   */
  @NotNull
  protected Action accept(@NotNull TreePath path, @NotNull AbstractTreeNode node, @NotNull T element) {
    if (found(node, element)) {
      LOG.debug("found ", path);
      if (predicate == null) return Action.INTERRUPT;
      if (predicate.test(path)) return Action.CONTINUE;
    }
    else if (contains(node, element)) {
      LOG.debug("visit ", path);
      return Action.CONTINUE;
    }
    return Action.SKIP_CHILDREN;
  }

  /**
   * @param node    a node of a tree structure
   * @param element an element to find
   * @return {@code true} if the specified node represents the given element
   */
  protected boolean found(@NotNull AbstractTreeNode node, @NotNull T element) {
    return node.canRepresent(element);
  }

  /**
   * @param node    a node of a tree structure
   * @param element an element to find
   * @return {@code true} if the specified node is an ancestor of the given element
   */
  protected boolean contains(@NotNull AbstractTreeNode node, @NotNull T element) {
    return isAncestor(node.getValue(), element);
  }

  /**
   * @param value   a content of a tree node
   * @param element an element to find
   * @return
   */
  protected boolean isAncestor(Object value, @NotNull T element) {
    return false;
  }
}
