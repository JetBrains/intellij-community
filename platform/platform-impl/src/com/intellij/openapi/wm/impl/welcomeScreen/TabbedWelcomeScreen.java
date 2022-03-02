// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WelcomeScreenCustomization;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNullElse;

public final class TabbedWelcomeScreen extends AbstractWelcomeScreen {
  DefaultMutableTreeNode root = new DefaultMutableTreeNode();
  DefaultTreeModel treeModel = new DefaultTreeModel(root);
  JTree tree = new Tree(treeModel);
  private JPanel leftPanel = new NonOpaquePanel();

  TabbedWelcomeScreen() {
    this(WelcomeTabFactory.WELCOME_TAB_FACTORY_EP.getExtensionList(), true, true);
  }

  public TabbedWelcomeScreen(List<WelcomeTabFactory> welcomeTabFactories, boolean addLogo, boolean addQuickAccessPanel) {
    setBackground(WelcomeScreenUIManager.getMainTabListBackground());

    CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> mainPanel = createCardPanel();
    for (WelcomeTabFactory tabFactory : welcomeTabFactories) {
      if (tabFactory.isApplicable()) {
        WelcomeScreenTab tab = tabFactory.createWelcomeTab(this);
        addTab(root, tab);
      }
    }

    TreeUtil.installActions(tree);

    tree.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setRootVisible(false);
    tree.setBackground(WelcomeScreenUIManager.getMainTabListBackground());
    tree.setBorder(JBUI.Borders.emptyLeft(16));
    tree.setCellRenderer(new MyCellRenderer());
    tree.setRowHeight(0);

    tree.addTreeSelectionListener(e -> {
      WelcomeScreenTab tab = TreeUtil.getUserObject(WelcomeScreenTab.class, e.getPath().getLastPathComponent());
      if (tab == null) return;
      mainPanel.select(tab, true);
      WelcomeScreenEventCollector.logTabSelected(tab);
    });
    tree.getAccessibleContext().setAccessibleName(UIBundle.message("welcome.screen.welcome.screen.categories.accessible.name"));

    if (addLogo) {
      JComponent logoComponent = WelcomeScreenComponentFactory.createSmallLogo();
      logoComponent.setFocusable(false);
      logoComponent.setBorder(JBUI.Borders.emptyLeft(16));
      leftPanel.add(logoComponent, BorderLayout.NORTH);
    }

    leftPanel.add(tree, BorderLayout.CENTER);

    if (addQuickAccessPanel) {
      JComponent quickAccessPanel = createQuickAccessPanel(this);
      quickAccessPanel.setBorder(JBUI.Borders.empty(5, 10));
      leftPanel.add(quickAccessPanel, BorderLayout.SOUTH);
    }

    leftPanel.setPreferredSize(new Dimension(JBUI.scale(215), leftPanel.getPreferredSize().height));

    JComponent centralPanel = mainPanel;
    JComponent mainPanelToolbar = createMainPanelToolbar(this);
    if (mainPanelToolbar != null) {
      centralPanel = new JPanel(new BorderLayout());
      centralPanel.add(mainPanel, BorderLayout.CENTER);
      centralPanel.add(mainPanelToolbar, BorderLayout.SOUTH);
    }

    add(leftPanel, BorderLayout.WEST);
    add(centralPanel, BorderLayout.CENTER);

    //select and install focused component
    if (root.getChildCount() > 0) {
      DefaultMutableTreeNode firstTabNode = (DefaultMutableTreeNode)root.getFirstChild();
      WelcomeScreenTab firstTab = TreeUtil.getUserObject(WelcomeScreenTab.class, firstTabNode);

      TreeUtil.selectNode(tree, firstTabNode);
      TreeUtil.expandAll(tree);

      JComponent firstShownPanel = firstTab.getAssociatedComponent();
      UiNotifyConnector.doWhenFirstShown(firstShownPanel, () -> {
        JComponent preferred = IdeFocusTraversalPolicy.getPreferredFocusedComponent(firstShownPanel);
        IdeFocusManager.getGlobalInstance().requestFocus(requireNonNullElse(preferred, tree), true);
        WelcomeScreenEventCollector.logWelcomeScreenShown();
      });
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    WelcomeScreenEventCollector.logWelcomeScreenHide();
  }

  public void setTabListVisible(boolean visible) {
    leftPanel.setVisible(visible);
  }

  @ApiStatus.Experimental
  public void selectTab(@NotNull WelcomeScreenTab tab) {
    TreeNode targetNode = TreeUtil.treeNodeTraverser(root).traverse(TreeTraversal.POST_ORDER_DFS).find((node) -> {
      if (node instanceof DefaultMutableTreeNode) {
        var currentTab = ((DefaultMutableTreeNode)node).getUserObject();
        if (currentTab == tab) {
          return true;
        }
      }
      return false;
    });

    if (targetNode != null) {
      TreeUtil.selectNode(tree, targetNode);
    }
  }


  @ApiStatus.Internal
  @ApiStatus.Experimental
  public void navigateToTabAndSetMainComponent(@NotNull DefaultWelcomeScreenTab tab, Component component) {
    int tabIndex = 0;
    boolean found = false;
    while (tabIndex < tree.getRowCount()) {
      var t = getTabByIndex(tabIndex);
      if (t == tab) {
        found = true;
        break;
      }
      tabIndex++;
    }
    if (!found) return;
    tree.setSelectionRow(tabIndex);

    var panel = (JComponent)tab.myAssociatedComponent.getComponent(0);
    panel.removeAll();
    panel.add(component, BorderLayout.CENTER);
    revalidate();
    repaint();
    leftPanel.setVisible(false);
  }

  private DefaultWelcomeScreenTab getTabByIndex(int index) {
    var tab = tree.getPathForRow(index).getLastPathComponent();
    if (tab == null) return null;
    if (tab instanceof DefaultMutableTreeNode) {
      var panel = ((DefaultMutableTreeNode)tab).getUserObject();
      if (panel instanceof DefaultWelcomeScreenTab) {
        return (DefaultWelcomeScreenTab)panel;
      }
    }
    return null;
  }

  private static void addTab(@NotNull DefaultMutableTreeNode parent, @NotNull WelcomeScreenTab tab) {
    DefaultMutableTreeNode child = new DefaultMutableTreeNode(tab);
    parent.add(child);
    tab.getChildTabs().forEach(it -> addTab(child, it));
  }

  private static JComponent createQuickAccessPanel(@NotNull Disposable parentDisposable) {
    JPanel quickAccessPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    WelcomeScreenCustomization.WELCOME_SCREEN_CUSTOMIZATION.getExtensionsIfPointIsRegistered().stream()
      .map(c -> c.createQuickAccessComponent(parentDisposable))
      .filter(Objects::nonNull)
      .forEach(quickAccessPanel::add);
    return quickAccessPanel;
  }

  @Nullable
  private static JComponent createMainPanelToolbar(@NotNull Disposable parentDisposable) {
    return WelcomeScreenCustomization.WELCOME_SCREEN_CUSTOMIZATION.getExtensionsIfPointIsRegistered().stream()
      .map(c -> c.createMainPanelToolbar(parentDisposable))
      .filter(Objects::nonNull)
      .findFirst().orElse(null);
  }

  private static @NotNull CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> createCardPanel() {
    return new CardLayoutPanel<>() {
      @Override
      protected WelcomeScreenTab prepare(WelcomeScreenTab key) {
        key.updateComponent();
        return key;
      }

      @Override
      protected JPanel create(WelcomeScreenTab screenTab) {
        return JBUI.Panels.simplePanel(screenTab.getAssociatedComponent());
      }
    };
  }

  private static final class MyCellRenderer implements TreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean isSelected,
                                                  boolean isExpanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean cellHasFocus) {
      WelcomeScreenTab tab = TreeUtil.getUserObject(WelcomeScreenTab.class, value);
      JComponent keyComponent = tab != null ? tab.getKeyComponent(tree) : new JLabel("");
      JPanel wrappedPanel = JBUI.Panels.simplePanel(keyComponent);
      UIUtil.setBackgroundRecursively(wrappedPanel, isSelected
                                                    ? UIUtil.getListSelectionBackground(cellHasFocus)
                                                    : WelcomeScreenUIManager.getMainTabListBackground());
      UIUtil.setForegroundRecursively(wrappedPanel, UIUtil.getListForeground(isSelected, cellHasFocus));
      if (tab instanceof Accessible) {
        wrappedPanel.getAccessibleContext().setAccessibleName(((Accessible)tab).getAccessibleContext().getAccessibleName());
      }
      return wrappedPanel;
    }
  }

  public abstract static class DefaultWelcomeScreenTab implements WelcomeScreenTab, Accessible {
    protected final JComponent myKeyComponent;
    private final JBLabel myLabel;
    private final WelcomeScreenEventCollector.TabType myType;
    private JComponent myAssociatedComponent;

    public DefaultWelcomeScreenTab(@NotNull @Nls String tabName) {
      this(tabName, null, WelcomeScreenEventCollector.TabType.TabNavOther);
    }

    public DefaultWelcomeScreenTab(@NotNull @Nls String tabName, @Nullable Icon icon) {
      this(tabName, icon, WelcomeScreenEventCollector.TabType.TabNavOther);
    }

    DefaultWelcomeScreenTab(@NotNull @Nls String tabName, @NotNull WelcomeScreenEventCollector.TabType tabType) {
      this(tabName, null, tabType);
    }

    DefaultWelcomeScreenTab(@NotNull @Nls String tabName, @Nullable Icon icon, @NotNull WelcomeScreenEventCollector.TabType tabType) {
      myLabel = new JBLabel(tabName, icon, SwingConstants.LEFT);
      myType = tabType;
      myKeyComponent = JBUI.Panels.simplePanel().addToLeft(myLabel).withBackground(WelcomeScreenUIManager.getMainTabListBackground())
        .withBorder(JBUI.Borders.empty(8, 0));
    }

    @Override
    @NotNull
    public JComponent getKeyComponent(@NotNull JComponent parent) {
      return myKeyComponent;
    }

    @Override
    @NotNull
    public JComponent getAssociatedComponent() {
      if (myAssociatedComponent == null) {
        myAssociatedComponent = buildComponent();
      }
      return myAssociatedComponent;
    }

    WelcomeScreenEventCollector.TabType getType() {
      return myType;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      return myLabel.getAccessibleContext();
    }

    protected abstract JComponent buildComponent();
  }
}
