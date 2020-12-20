// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WelcomeScreenCustomization;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.createSmallLogo;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getMainTabListBackground;
import static com.intellij.ui.UIBundle.message;

public final class TabbedWelcomeScreen extends AbstractWelcomeScreen {
  TabbedWelcomeScreen() {
    setBackground(getMainTabListBackground());

    CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> centralPanel = createCardPanel();

    DefaultListModel<WelcomeScreenTab> mainListModel = new DefaultListModel<>();
    WelcomeTabFactory.WELCOME_TAB_FACTORY_EP.getExtensionList().forEach(it -> mainListModel.addElement(it.createWelcomeTab(this)));

    JBList<WelcomeScreenTab> tabList = createListWithTabs(mainListModel);
    tabList.addListSelectionListener(e -> centralPanel.select(tabList.getSelectedValue(), true));
    tabList.getAccessibleContext().setAccessibleName(message("welcome.screen.welcome.screen.categories.accessible.name"));

    JComponent logoComponent = createSmallLogo();
    logoComponent.setFocusable(false);
    logoComponent.setBorder(JBUI.Borders.emptyLeft(16));

    JPanel leftPanel = new NonOpaquePanel();
    leftPanel.add(logoComponent, BorderLayout.NORTH);
    leftPanel.add(tabList, BorderLayout.CENTER);

    JComponent quickAccessPanel = createQuickAccessPanel(this);
    quickAccessPanel.setBorder(JBUI.Borders.empty(5, 10));
    leftPanel.add(quickAccessPanel, BorderLayout.SOUTH);
    leftPanel.setPreferredSize(new Dimension(JBUI.scale(196), leftPanel.getPreferredSize().height));

    add(leftPanel, BorderLayout.WEST);
    add(centralPanel, BorderLayout.CENTER);

    //select and install focused component
    if (!mainListModel.isEmpty()) {
      tabList.setSelectedIndex(0);
      JComponent firstShownPanel = mainListModel.get(0).getAssociatedComponent();
      UiNotifyConnector.doWhenFirstShown(firstShownPanel, () -> IdeFocusManager.getGlobalInstance()
        .requestFocus(IdeFocusTraversalPolicy.getPreferredFocusedComponent(firstShownPanel), true));
    }
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
    tabList.setBackground(getMainTabListBackground());
    tabList.setBorder(JBUI.Borders.emptyLeft(16));
    tabList.setCellRenderer(new MyCellRenderer());
    return tabList;
  }

  private static JComponent createQuickAccessPanel(@NotNull Disposable parentDisposable) {
    JPanel quickAccessPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    StreamEx.of(WelcomeScreenCustomization.WELCOME_SCREEN_CUSTOMIZATION.getExtensionsIfPointIsRegistered())
      .map(c -> c.createQuickAccessComponent(parentDisposable))
      .nonNull()
      .forEach(quickAccessPanel::add);
    return quickAccessPanel;
  }

  @NotNull
  private static CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> createCardPanel() {
    return new CardLayoutPanel<>() {
      @Override
      protected WelcomeScreenTab prepare(WelcomeScreenTab key) {
        return key;
      }

      @Override
      protected JPanel create(WelcomeScreenTab screenTab) {
        return UI.Panels.simplePanel(screenTab.getAssociatedComponent());
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
      JComponent keyComponent = value.getKeyComponent();
      JPanel wrappedPanel = JBUI.Panels.simplePanel(keyComponent);
      UIUtil.setBackgroundRecursively(wrappedPanel, isSelected ? UIUtil.getListSelectionBackground(cellHasFocus): getMainTabListBackground());
      UIUtil.setForegroundRecursively(wrappedPanel, UIUtil.getListForeground(isSelected, cellHasFocus));
      if (value instanceof Accessible) {
        wrappedPanel.getAccessibleContext().setAccessibleName(((Accessible)value).getAccessibleContext().getAccessibleName());
      }
      return wrappedPanel;
    }
  }

  public abstract static class DefaultWelcomeScreenTab implements WelcomeScreenTab, Accessible {

    private final JComponent myKeyComponent;
    private JComponent myAssociatedComponent;
    private final JBLabel myLabel;

    public DefaultWelcomeScreenTab(@NotNull @Nls String tabName) {
      myLabel = new JBLabel(tabName);
      myKeyComponent = JBUI.Panels.simplePanel().addToLeft(myLabel).withBackground(getMainTabListBackground())
        .withBorder(JBUI.Borders.empty(8, 0));
    }

    @Override
    @NotNull
    public JComponent getKeyComponent() {
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

    @Override
    public AccessibleContext getAccessibleContext() {
      return myLabel.getAccessibleContext();
    }

    protected abstract JComponent buildComponent();
  }
}