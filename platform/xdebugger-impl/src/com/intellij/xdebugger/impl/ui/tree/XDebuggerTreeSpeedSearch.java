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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.Collections;
import java.util.ListIterator;

class XDebuggerTreeSpeedSearch extends TreeSpeedSearch {

  public final int SEARCH_DEPTH = Registry.intValue("debugger.variablesView.rss.depth");

  XDebuggerTreeSpeedSearch(XDebuggerTree tree, Convertor<? super TreePath, String> toStringConvertor) {
    super(tree, toStringConvertor, true);
    setComparator(new SpeedSearchComparator(false, false) {

      @Override
      public int matchingDegree(String pattern, String text) {
        return matchingFragments(pattern, text) != null ? 1 : 0;
      }

      @Nullable
      @Override
      public Iterable<TextRange> matchingFragments(@NotNull String pattern, @NotNull String text) {
        int index = StringUtil.indexOfIgnoreCase(text, pattern, 0);
        return index >= 0 ? Collections.singleton(TextRange.from(index, pattern.length())) : null;
      }
    });
  }

  @Nullable
  @Override
  protected Object findElement(String s) {
    int selectedIndex = getSelectedIndex();
    if (selectedIndex < 0) {
      selectedIndex = 0;
    }
    final ListIterator<Object> it = getElementIterator(selectedIndex);
    final String _s = s.trim();

    // search visible nodes at first
    while (it.hasNext()) {
      final TreePath element = (TreePath) it.next();
      if (myComponent.isVisible(element) && isMatchingElement(element, _s)) return element;
    }
    if (selectedIndex > 0) {
      while (it.hasPrevious()) it.previous();
      while (it.hasNext() && it.nextIndex() != selectedIndex) {
        final TreePath element = (TreePath) it.next();
        if (myComponent.isVisible(element) && isMatchingElement(element, _s)) return element;
      }
    }

    while (it.hasNext()) {
      final TreePath element = (TreePath) it.next();
      if (isMatchingElement(element, _s)) return element;
    }
    if (selectedIndex > 0) {
      while (it.hasPrevious()) it.previous();
      while (it.hasNext() && it.nextIndex() != selectedIndex) {
        final TreePath element = (TreePath) it.next();
        if (isMatchingElement(element, _s)) return element;
      }
    }

    return null;
  }

  @NotNull
  @Override
  protected Object[] getAllElements() {
    XDebuggerTreeNode root = ObjectUtils.tryCast(myComponent.getModel().getRoot(), XDebuggerTreeNode.class);
    int initialLevel = root != null ? root.getPath().getPathCount() : 0;

    return TreeUtil.treePathTraverser(myComponent)
        .expand(n -> myComponent.isExpanded(n) || n.getPathCount() - initialLevel < SEARCH_DEPTH)
        .traverse()
        .filter(o -> !(o.getLastPathComponent() instanceof LoadingNode))
        .toArray(TreeUtil.EMPTY_TREE_PATH);
  }
}