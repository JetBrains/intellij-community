// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.Objects;

import static java.util.Objects.requireNonNullElse;

public final class TabbedWelcomeScreen extends AbstractWelcomeScreen {

  TabbedWelcomeScreen() {
    setBackground(WelcomeScreenUIManager.getMainTabListBackground());

    CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> mainPanel = createCardPanel();

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    for (WelcomeTabFactory tabFactory : WelcomeTabFactory.WELCOME_TAB_FACTORY_EP.getExtensionList()) {
      if (tabFactory.isApplicable()) {
        WelcomeScreenTab tab = tabFactory.createWelcomeTab(this);
        addTab(root, tab);
      }
    }
    DefaultTreeModel treeModel = new DefaultTreeModel(root);

    JTree tree = new Tree(treeModel);
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

    JComponent logoComponent = WelcomeScreenComponentFactory.createSmallLogo();
    logoComponent.setFocusable(false);
    logoComponent.setBorder(JBUI.Borders.emptyLeft(16));

    JPanel leftPanel = new NonOpaquePanel();
    leftPanel.add(logoComponent, BorderLayout.NORTH);
    leftPanel.add(tree, BorderLayout.CENTER);

    JComponent quickAccessPanel = createQuickAccessPanel(this);
    quickAccessPanel.setBorder(JBUI.Borders.empty(5, 10));
    leftPanel.add(quickAccessPanel, BorderLayout.SOUTH);
    leftPanel.setPreferredSize(new Dimension(JBUI.scale(196), leftPanel.getPreferredSize().height));

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

  private static void addTab(@NotNull DefaultMutableTreeNode parent, @NotNull WelcomeScreenTab tab) {
    DefaultMutableTreeNode child = new DefaultMutableTreeNode(tab);
    parent.add(child);
    tab.getChildTabs().forEach(it -> addTab(child, it));
  }

  @Override
  public void dispose() {
    super.dispose();
    WelcomeScreenEventCollector.logWelcomeScreenHide();
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
        return key;
      }

      @Override
      protected JPanel create(WelcomeScreenTab screenTab) {
        return JBUI.Panels.simplePanel(screenTab.getAssociatedComponent());
      }
    };
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    return null;
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
    private JComponent myAssociatedComponent;
    private final JBLabel myLabel;
    private final WelcomeScreenEventCollector.TabType myType;

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
