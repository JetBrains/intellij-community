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

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.TextRange;
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

  private XDebuggerTreeSearchSession mySearchSession;

  XDebuggerTreeSpeedSearch(XDebuggerTree tree, Convertor<? super TreePath, String> toStringConvertor) {
    super(tree, toStringConvertor, false);
    setComparator(new SpeedSearchComparator(false, false) {

      @Override
      public int matchingDegree(String pattern, String text) {
        return matchingFragments(pattern, text) != null ? 1 : 0;
      }

      @Nullable
      @Override
      public Iterable<TextRange> matchingFragments(@NotNull String pattern, @NotNull String text) {
        myRecentSearchText = pattern;
        boolean sensitive = false;
        if (mySearchSession != null) {
           sensitive = mySearchSession.getFindModel().isCaseSensitive();
        }

        int index = sensitive ? StringUtil.indexOf(text, pattern, 0)
                              : StringUtil.indexOfIgnoreCase(text, pattern, 0);

        return index >= 0 ? Collections.singleton(TextRange.from(index, pattern.length())) : null;
      }
    });
  }

  boolean findOccurence(@NotNull String searchQuery){
    Object element = findElement(searchQuery);
    boolean found = element != null;
    if (found) {
      selectElement(element, searchQuery);
    }
    return found;
  }

  void searchSessionStarted(XDebuggerTreeSearchSession searchSession) {
    mySearchSession = searchSession;
    myCanExpand = true;
  }

  void searchSessionStopped() {
    mySearchSession = null;
    myCanExpand = false;
  }

  @Override
  public boolean isPopupActive() {
    if (mySearchSession != null) {
      return true;
    }
    return super.isPopupActive();
  }

  boolean nextOccurence(@NotNull String searchQuery) {
    final int selectedIndex = getSelectedIndex();
    final ListIterator<?> it = getElementIterator(selectedIndex + 1);
    final Object current;
    if (it.hasPrevious()) {
      current = it.previous();
      it.next();
    }
    else {
      current = null;
    }
    final String _s = searchQuery.trim();
    while (it.hasNext()) {
      final Object element = it.next();
      if (isMatchingElement(element, _s)) {
        selectElement(element, searchQuery);
        return true;
      }
    }

    if (UISettings.getInstance().getCycleScrolling()) {
      final ListIterator<Object> i = getElementIterator(0);
      while (i.hasNext()) {
        final Object element = i.next();
        if (isMatchingElement(element, _s)) {
          selectElement(element, searchQuery);
          return true;
        }
      }
    }

    if (current != null && isMatchingElement(current, _s)) {
      selectElement(current, searchQuery);
      return true;
    } else {
      return false;
    }
  }

  boolean previousOccurence(@NotNull String searchQuery) {
    final int selectedIndex = getSelectedIndex();
    if (selectedIndex < 0) return false;
    final ListIterator<?> it = getElementIterator(selectedIndex);
    final Object current;
    if (it.hasNext()) {
      current = it.next();
      it.previous();
    }
    else {
      current = null;
    }
    final String _s = searchQuery.trim();
    while (it.hasPrevious()) {
      final Object element = it.previous();
      if (isMatchingElement(element, _s)) {
        selectElement(element, searchQuery);
        return true;
      }
    }

    if (UISettings.getInstance().getCycleScrolling()) {
      final ListIterator<Object> i = getElementIterator(getElementCount());
      while (i.hasPrevious()) {
        final Object element = i.previous();
        if (isMatchingElement(element, _s)) {
          selectElement(element, searchQuery);
          return true;
        }
      }
    }

    if (current != null && isMatchingElement(current, _s)) {
      selectElement(current, searchQuery);
      return true;
    }
    else {
      return false;
    }
  }

  @Nullable
  @Override
  protected Object findElement(@NotNull String s) {
    if (!myCanExpand) return super.findElement(s);

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

  @Override
  protected Object @NotNull [] getAllElements() {
    if (!myCanExpand) return super.getAllElements();

    XDebuggerTreeNode root = ObjectUtils.tryCast(myComponent.getModel().getRoot(), XDebuggerTreeNode.class);
    int initialLevel = root != null ? root.getPath().getPathCount() : 0;

    return TreeUtil.treePathTraverser(myComponent)
        .expand(n -> myComponent.isExpanded(n) || n.getPathCount() - initialLevel < getSearchDepth())
        .traverse()
        .filter(o -> !(o.getLastPathComponent() instanceof LoadingNode))
        .toArray(TreeUtil.EMPTY_TREE_PATH);
  }

  private int getSearchDepth() {
    return mySearchSession.getFindModel().getSearchDepth();
  }
}