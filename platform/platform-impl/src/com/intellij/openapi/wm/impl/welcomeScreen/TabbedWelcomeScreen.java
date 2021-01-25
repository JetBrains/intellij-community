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
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public final class TabbedWelcomeScreen extends AbstractWelcomeScreen {
  private final JBList<WelcomeScreenTab> tabList;
  TabbedWelcomeScreen() {
    setBackground(WelcomeScreenUIManager.getMainTabListBackground());

    CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> mainPanel = createCardPanel();

    DefaultListModel<WelcomeScreenTab> mainListModel = new DefaultListModel<>();
    for (WelcomeTabFactory tabFactory : WelcomeTabFactory.WELCOME_TAB_FACTORY_EP.getExtensionList()) {
      if (tabFactory.isApplicable()) {
        mainListModel.addElement(tabFactory.createWelcomeTab(this));
      }
    }

    tabList = createListWithTabs(mainListModel);
    tabList.addListSelectionListener(e -> {
      mainPanel.select(tabList.getSelectedValue(), true);
      WelcomeScreenEventCollector.logTabSelected(tabList.getSelectedValue());
    });
    tabList.getAccessibleContext().setAccessibleName(UIBundle.message("welcome.screen.welcome.screen.categories.accessible.name"));

    JComponent logoComponent = WelcomeScreenComponentFactory.createSmallLogo();
    logoComponent.setFocusable(false);
    logoComponent.setBorder(JBUI.Borders.emptyLeft(16));

    JPanel leftPanel = new NonOpaquePanel();
    leftPanel.add(logoComponent, BorderLayout.NORTH);
    leftPanel.add(tabList, BorderLayout.CENTER);

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
    if (!mainListModel.isEmpty()) {
      tabList.setSelectedIndex(0);
      JComponent firstShownPanel = mainListModel.get(0).getAssociatedComponent();
      UiNotifyConnector.doWhenFirstShown(firstShownPanel, () -> {
        IdeFocusManager.getGlobalInstance()
          .requestFocus(IdeFocusTraversalPolicy.getPreferredFocusedComponent(firstShownPanel), true);
        WelcomeScreenEventCollector.logWelcomeScreenShown();
      });
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    WelcomeScreenEventCollector.logWelcomeScreenHide();
  }

  @NotNull
  private static JBList<WelcomeScreenTab> createListWithTabs(@NotNull DefaultListModel<WelcomeScreenTab> mainListModel) {
    JBList<WelcomeScreenTab> tabList = new JBList<>(mainListModel) {
      @Override
      public int locationToIndex(Point location) {
        int i = super.locationToIndex(location);
        return (i == -1 || !getCellBounds(i, i).contains(location)) ? -1 : i;
      }
    };
    tabList.setBackground(WelcomeScreenUIManager.getMainTabListBackground());
    tabList.setBorder(JBUI.Borders.emptyLeft(16));
    tabList.setCellRenderer(new MyCellRenderer());
    return tabList;
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

  private static final class MyCellRenderer extends CellRendererPane implements ListCellRenderer<WelcomeScreenTab> {
    @Override
    public Component getListCellRendererComponent(JList<? extends WelcomeScreenTab> list,
                                                  WelcomeScreenTab value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      JComponent keyComponent = value.getKeyComponent(list);
      JPanel wrappedPanel = JBUI.Panels.simplePanel(keyComponent);
      UIUtil.setBackgroundRecursively(wrappedPanel, isSelected ? UIUtil.getListSelectionBackground(cellHasFocus): WelcomeScreenUIManager
        .getMainTabListBackground());
      UIUtil.setForegroundRecursively(wrappedPanel, UIUtil.getListForeground(isSelected, cellHasFocus));
      if (value instanceof Accessible) {
        wrappedPanel.getAccessibleContext().setAccessibleName(((Accessible)value).getAccessibleContext().getAccessibleName());
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
      this(tabName, WelcomeScreenEventCollector.TabType.TabNavOther);
    }

    DefaultWelcomeScreenTab(@NotNull @Nls String tabName, @NotNull WelcomeScreenEventCollector.TabType tabType) {
      myLabel = new JBLabel(tabName);
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

  public int getSelectedIndex(){
    return tabList.getSelectedIndex();
  }

  public void setSelectedIndex(int idx){
    tabList.setSelectedIndex(idx);
  }
}