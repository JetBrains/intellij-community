// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collections;
import java.util.ListIterator;
import java.util.function.Function;

final class XDebuggerTreeSpeedSearch extends TreeSpeedSearchInsideCollapsedNodes {
  private static final String CAN_EXPAND_PROPERTY = "debugger.speed.search.tree.option.can.expand";
  private static final String COUNTER_PROPERTY = "debugger.speed.search.tree.option.hint.counter";
  public final int SEARCH_DEPTH = Registry.intValue("debugger.variablesView.rss.depth");

  private XDebuggerTreeSpeedSearch(XDebuggerTree tree, Function<? super TreePath, String> toStringConvertor) {
    super(tree,
          PropertiesComponent.getInstance().getBoolean(CAN_EXPAND_PROPERTY, false),
          FindBundle.message("find.expand.nodes"),
          toStringConvertor);

    setComparator(new SpeedSearchComparator(false, false) {

      @Override
      public int matchingDegree(String pattern, String text) {
        return matchingFragments(pattern, text) != null ? 1 : 0;
      }

      @Override
      public @Nullable Iterable<TextRange> matchingFragments(@NotNull String pattern, @NotNull String text) {
        myRecentSearchText = pattern;
        int index = StringUtil.indexOfIgnoreCase(text, pattern, 0);
        return index >= 0 ? Collections.singleton(TextRange.from(index, pattern.length())) : null;
      }
    });
  }

  @Contract("_, _ -> new")
  static @NotNull XDebuggerTreeSpeedSearch installOn(XDebuggerTree tree, Function<? super TreePath, String> toStringConvertor) {
    XDebuggerTreeSpeedSearch search = new XDebuggerTreeSpeedSearch(tree, toStringConvertor);
    search.setupListeners();
    return search;
  }

  @Override
  public void setCanExpand(boolean canExpand) {
    super.setCanExpand(canExpand);
    PropertiesComponent.getInstance().setValue(CAN_EXPAND_PROPERTY, canExpand);
  }

  @Override
  protected @Nullable Object findNextElement(String s) {
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
    final String _s = s.trim();
    while (it.hasNext()) {
      final Object element = it.next();
      if (isMatchingElement(element, _s)) return element;
    }

    if (!myCanExpand) {
      showHint();
    }
    if (UISettings.getInstance().getCycleScrolling()) {
      final ListIterator<Object> i = getElementIterator(0);
      while (i.hasNext()) {
        final Object element = i.next();
        if (isMatchingElement(element, _s)) return element;
      }
    }

    return current != null && isMatchingElement(current, _s) ? current : null;
  }

  @Override
  protected @Nullable Object findElement(@NotNull String s) {
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
    if (selectedIndex > 0 || myCanExpand) {
      while (it.hasPrevious()) it.previous();
      while (it.hasNext() && it.nextIndex() != selectedIndex) {
        final TreePath element = (TreePath) it.next();
        if (myComponent.isVisible(element) && isMatchingElement(element, _s)) return element;
      }
    }

    if (myCanExpand) {
      while (it.hasNext()) {
        final TreePath element = (TreePath)it.next();
        if (isMatchingElement(element, _s)) return element;
      }
      if (selectedIndex > 0) {
        while (it.hasPrevious()) it.previous();
        while (it.hasNext() && it.nextIndex() != selectedIndex) {
          final TreePath element = (TreePath)it.next();
          if (isMatchingElement(element, _s)) return element;
        }
      }
    }

    return null;
  }

  @Override
  protected void showHint() {
    showHint(getSearchOptionButton());
  }

  private static void showHint(JComponent component) {
    if (component == null) {
      return;
    }

    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    int counter = propertiesComponent.getInt(COUNTER_PROPERTY, 0);
    if (counter >= 1) {
      return;
    }

    JComponent label = HintUtil.createInformationLabel(new SimpleColoredText(FindBundle.message("find.expand.nodes"),
                                                                             SimpleTextAttributes.REGULAR_ATTRIBUTES));
    LightweightHint hint = new LightweightHint(label);

    Point point = new Point(component.getWidth() / 2,  0);
    final HintHint hintHint = new HintHint(component, point)
      .setPreferredPosition(Balloon.Position.above)
      .setAwtTooltip(true)
      .setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD))
      .setBorderColor(HintUtil.getHintBorderColor())
      .setTextBg(HintUtil.getInformationColor())
      .setShowImmediately(true);

    ApplicationManager.getApplication().invokeLater(() -> {
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      hint.show(component, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hintHint);
    });


    propertiesComponent.setValue(COUNTER_PROPERTY, counter + 1, 0);
  }

  @Override
  protected @NotNull JBIterable<TreePath> allPaths() {
    XDebuggerTreeNode root = ObjectUtils.tryCast(myComponent.getModel().getRoot(), XDebuggerTreeNode.class);
    int initialLevel = root != null ? root.getPath().getPathCount() : 0;

    return TreeUtil.treePathTraverser(myComponent)
        .expand(n -> myComponent.isExpanded(n) || (myCanExpand && n.getPathCount() - initialLevel < SEARCH_DEPTH))
        .traverse()
        .filter(o -> !(o.getLastPathComponent() instanceof LoadingNode
                       || (o.equals(root.getPath()) && !myComponent.isRootVisible())));
  }
}