// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.ListIterator;

import static com.intellij.openapi.actionSystem.ex.ActionUtil.getMnemonicAsShortcut;

class XDebuggerTreeSpeedSearch extends TreeSpeedSearch {

  public final int SEARCH_DEPTH = Registry.intValue("debugger.variablesView.rss.depth");
  private static final String COUNTER_PROPERTY = "debugger.speed.search.tree.option.hint.counter";
  private static final String CAN_EXPAND_PROPERTY = "debugger.speed.search.tree.option.can.expand";
  private MyActionButton mySearchOption = null;
  private ShortcutSet myOptionShortcutSet;

  XDebuggerTreeSpeedSearch(XDebuggerTree tree, Convertor<? super TreePath, String> toStringConvertor) {
    super(tree, toStringConvertor, PropertiesComponent.getInstance().getBoolean(CAN_EXPAND_PROPERTY, false));
    setComparator(new SpeedSearchComparator(false, false) {

      @Override
      public int matchingDegree(String pattern, String text) {
        return matchingFragments(pattern, text) != null ? 1 : 0;
      }

      @Nullable
      @Override
      public Iterable<TextRange> matchingFragments(@NotNull String pattern, @NotNull String text) {
        myRecentSearchText = pattern;
        int index = StringUtil.indexOfIgnoreCase(text, pattern, 0);
        return index >= 0 ? Collections.singleton(TextRange.from(index, pattern.length())) : null;
      }
    });

    setSearchOption(new SearchCollapsedNodesAction());
  }

  @Override
  @Nullable
  protected Object findNextElement(String s) {
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
      showHint(mySearchOption);
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



  @Nullable
  @Override
  protected Object findElement(@NotNull String s) {
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

  private static void showHint(JComponent component) {
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
      .setTextBg(HintUtil.getInformationColor())
      .setShowImmediately(true);

    ApplicationManager.getApplication().invokeLater(() -> {
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      hint.show(component, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hintHint);
    });


    propertiesComponent.setValue(COUNTER_PROPERTY, counter + 1, 0);
  }

  @NotNull
  @Override
  protected JBIterable<TreePath> allPaths() {
    XDebuggerTreeNode root = ObjectUtils.tryCast(myComponent.getModel().getRoot(), XDebuggerTreeNode.class);
    int initialLevel = root != null ? root.getPath().getPathCount() : 0;

    return TreeUtil.treePathTraverser(myComponent)
        .expand(n -> myComponent.isExpanded(n) || (myCanExpand && n.getPathCount() - initialLevel < SEARCH_DEPTH))
        .traverse()
        .filter(o -> !(o.getLastPathComponent() instanceof LoadingNode
                       || (o.equals(root.getPath()) && !myComponent.isRootVisible())));
  }

  protected void setSearchOption(AnAction searchOption) {
    mySearchOption = new MyActionButton(searchOption, false);
    myOptionShortcutSet = getMnemonicAsShortcut(searchOption);
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    if (mySearchOption.isShowing() && myOptionShortcutSet != null) {
      KeyStroke eventKeyStroke = KeyStroke.getKeyStrokeForEvent(e);
      boolean match = Arrays.stream(myOptionShortcutSet.getShortcuts())
        .filter(s -> s.isKeyboard())
        .map(s -> ((KeyboardShortcut)s))
        .anyMatch(s -> eventKeyStroke.equals(s.getFirstKeyStroke()) || eventKeyStroke.equals(s.getSecondKeyStroke()));
      if (match) {
        mySearchOption.click();
        e.consume();
        return;
      }
    }
    super.processKeyEvent(e);
  }

  @Override
  protected @NotNull SearchPopup createPopup(String s) {
    return new DebuggerSearchPopup(s);
  }

  protected class DebuggerSearchPopup extends SearchPopup {
    protected final JPanel myIconsPanel = new NonOpaquePanel();

    protected DebuggerSearchPopup(String initialString) {
      super(initialString);
      add(myIconsPanel, BorderLayout.EAST);
      myIconsPanel.setBorder(JBUI.Borders.emptyRight(5));
      if (mySearchOption != null) {
        myIconsPanel.add(mySearchOption);
      }

    }

    @Override
    protected void handleInsert(String newText) {
      if (findElement(newText) == null) {
        mySearchField.setForeground(ERROR_FOREGROUND_COLOR);
        if(!myCanExpand) {
          showHint(mySearchOption);
        }
      }
      else {
        mySearchField.setForeground(FOREGROUND_COLOR);
      }
    }
  }

  private class SearchCollapsedNodesAction extends ToggleAction implements TooltipDescriptionProvider {
    SearchCollapsedNodesAction() {
      super(FindBundle.message("find.expand.nodes"));
      getTemplatePresentation().setIcon(AllIcons.General.Tree);
      getTemplatePresentation().setHoveredIcon(AllIcons.General.TreeHovered);
      getTemplatePresentation().setSelectedIcon(AllIcons.General.TreeSelected);
      ShortcutSet shortcut = getMnemonicAsShortcut(this);
      if (shortcut != null) {
        setShortcutSet(shortcut);
      }
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myCanExpand;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance().setValue(CAN_EXPAND_PROPERTY, state);
      setCanExpand(state);
    }
  }

  private static final class MyActionButton extends ActionButton {

    private MyActionButton(@NotNull AnAction action, boolean focusable) {
      super(action, action.getTemplatePresentation().clone(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
      setLook(ActionButtonLook.INPLACE_LOOK);
      setFocusable(true);
      updateIcon();
    }

    @Override
    protected DataContext getDataContext() {
      return DataManager.getInstance().getDataContext(this);
    }

    @Override
    public int getPopState() {
      return isSelected() ? SELECTED : super.getPopState();
    }

    @Override
    public Icon getIcon() {
      if (isEnabled() && isSelected()) {
        Icon selectedIcon = myPresentation.getSelectedIcon();
        if (selectedIcon != null) return selectedIcon;
      }
      return super.getIcon();
    }
  }
}