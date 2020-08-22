// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.wm.IdeFocusManager;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.createSmallLogo;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getMainTabListBackground;

public class TabbedWelcomeScreen extends AbstractWelcomeScreen {

  TabbedWelcomeScreen() {
    setBackground(getMainTabListBackground());

    CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> centralPanel = createCardPanel();

    DefaultListModel<WelcomeScreenTab> mainListModel = new DefaultListModel<>();
    WelcomeTabFactory.WELCOME_TAB_FACTORY_EP.getExtensionList().forEach(it -> mainListModel.addElement(it.createWelcomeTab(this)));

    JBList<WelcomeScreenTab> tabList = createListWithTabs(mainListModel);
    tabList.addListSelectionListener(e -> centralPanel.select(tabList.getSelectedValue(), true));

    JComponent logoComponent = createSmallLogo();
    logoComponent.setBorder(JBUI.Borders.emptyLeft(16));

    JPanel leftPanel = new NonOpaquePanel();
    leftPanel.add(logoComponent, BorderLayout.NORTH);
    leftPanel.add(tabList, BorderLayout.CENTER);
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
  private static JBList<WelcomeScreenTab> createListWithTabs(@Nullable DefaultListModel<WelcomeScreenTab> mainListModel) {
    JBList<WelcomeScreenTab> tabList = new JBList<WelcomeScreenTab>(mainListModel) {
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

  @NotNull
  private static CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> createCardPanel() {
    return new CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel>() {
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

  @Override
  public void setupFrame(JFrame frame) {
  }

  private static class MyCellRenderer extends CellRendererPane implements ListCellRenderer<WelcomeScreenTab> {

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
      return wrappedPanel;
    }
  }

  public abstract static class DefaultWelcomeScreenTab implements WelcomeScreenTab {

    private final JComponent myKeyComponent;
    private JComponent myAssociatedComponent;

    public DefaultWelcomeScreenTab(@NotNull @Nls String tabName) {
      myKeyComponent = JBUI.Panels.simplePanel().addToLeft(new JBLabel(tabName)).withBackground(getMainTabListBackground())
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

    protected abstract JComponent buildComponent();
  }
}