// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBSlidingPanel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.createSmallLogo;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getMainTabListBackground;

public class TabbedWelcomeScreen extends AbstractWelcomeScreen {
  private final JBSlidingPanel mySlidingPanel = new JBSlidingPanel();


  TabbedWelcomeScreen() {
    mySlidingPanel.add("root", this);
    setBackground(getMainTabListBackground());

    CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> centralPanel = createCardPanel();

    DefaultListModel<WelcomeScreenTab> mainListModel = new DefaultListModel<>();
    WelcomeTabFactory.WELCOME_TAB_FACTORY_EP.getExtensionList().forEach(it -> mainListModel.addElement(it.createWelcomeTab(this)));

    JBList<WelcomeScreenTab> tabList = new JBList<>(mainListModel);
    tabList.setBackground(getMainTabListBackground());
    tabList.setBorder(JBUI.Borders.emptyLeft(16));
    tabList.setFixedCellHeight(JBUI.scale(32));
    tabList.setCellRenderer(new MyCellRenderer());
    tabList.addListSelectionListener(e -> centralPanel.select(tabList.getSelectedValue(), true));
    tabList.setSelectedIndex(0);

    JComponent logoComponent = createSmallLogo();
    logoComponent.setBorder(JBUI.Borders.emptyLeft(16));

    JPanel leftPanel = new NonOpaquePanel();
    leftPanel.add(logoComponent, BorderLayout.NORTH);
    leftPanel.add(tabList, BorderLayout.CENTER);
    leftPanel.setPreferredSize(new Dimension(JBUI.scale(196), leftPanel.getPreferredSize().height));

    add(leftPanel, BorderLayout.WEST);
    add(centralPanel, BorderLayout.CENTER);
  }

  @Override
  public @Nullable BalloonLayout getBalloonLayout() {
    return null;
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
  public JComponent getWelcomePanel() {
    return mySlidingPanel;
  }

  @Override
  public void setupFrame(JFrame frame) {
  }

  @Override
  public void dispose() {

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

    public DefaultWelcomeScreenTab(@NotNull String tabName) {
      myKeyComponent = JBUI.Panels.simplePanel().addToLeft(new JLabel(tabName)).withBackground(getMainTabListBackground());
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