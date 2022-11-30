// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WelcomeScreenCustomization;
import com.intellij.openapi.wm.WelcomeScreenLeftPanel;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class TabbedWelcomeScreen extends AbstractWelcomeScreen {
  private final JPanel leftSidebarHolder = new NonOpaquePanel();
  private final WelcomeScreenLeftPanel myLeftSidebar;
  private final CardLayoutPanel<WelcomeScreenTab, WelcomeScreenTab, JPanel> mainPanel;
  private Disposable currentDisposable = null;

  TabbedWelcomeScreen() {
    this(WelcomeTabFactory.WELCOME_TAB_FACTORY_EP.getExtensionList(),
         new TreeWelcomeScreenLeftPanel(), true, true);
  }

  public TabbedWelcomeScreen(List<? extends WelcomeTabFactory> welcomeTabFactories, WelcomeScreenLeftPanel leftSidebar, boolean addLogo, boolean addQuickAccessPanel) {
    setBackground(WelcomeScreenUIManager.getMainTabListBackground());

    mainPanel = createCardPanel();

    myLeftSidebar = leftSidebar;
    myLeftSidebar.addSelectionListener(this, tab -> {
      mainPanel.select(tab, true);
      WelcomeScreenEventCollector.logTabSelected(tab);
    });

    if (addLogo) {
      JComponent logoComponent = WelcomeScreenComponentFactory.createSmallLogo();
      logoComponent.setFocusable(false);
      logoComponent.setBorder(JBUI.Borders.emptyLeft(16));
      leftSidebarHolder.add(logoComponent, BorderLayout.NORTH);
    }

    leftSidebarHolder.add(myLeftSidebar.getComponent(), BorderLayout.CENTER);

    if (addQuickAccessPanel) {
      JComponent quickAccessPanel = createQuickAccessPanel(this);
      quickAccessPanel.setBorder(JBUI.Borders.empty(5, 10));
      leftSidebarHolder.add(quickAccessPanel, BorderLayout.SOUTH);
    }

    leftSidebarHolder.setPreferredSize(new Dimension(JBUI.scale(215), leftSidebarHolder.getPreferredSize().height));

    JComponent centralPanel = mainPanel;
    JComponent mainPanelToolbar = createMainPanelToolbar(this);
    if (mainPanelToolbar != null) {
      centralPanel = new JPanel(new BorderLayout());
      centralPanel.add(mainPanel, BorderLayout.CENTER);
      centralPanel.add(mainPanelToolbar, BorderLayout.SOUTH);
    }

    add(leftSidebarHolder, BorderLayout.WEST);
    add(centralPanel, BorderLayout.CENTER);

    loadTabs(welcomeTabFactories);
  }

  @Override
  public void dispose() {
    super.dispose();
    WelcomeScreenEventCollector.logWelcomeScreenHide();
  }

  public void addSelectionListener(@NotNull Disposable disposable, @NotNull Consumer<? super WelcomeScreenTab> action) {
    myLeftSidebar.addSelectionListener(disposable, action);
  }

  /**
   * Prefer to use addSelectionListener(Disposable, Consumer)
   */
  public void addSelectionListener(@NotNull Consumer<? super WelcomeScreenTab> action) {
    myLeftSidebar.addSelectionListener(this, action);
  }

  public void loadTabs() {
    loadTabs(WelcomeTabFactory.WELCOME_TAB_FACTORY_EP.getExtensionList());
  }

  private void loadTabs(List<? extends WelcomeTabFactory> welcomeTabFactories) {
    myLeftSidebar.removeAllTabs();
    if (currentDisposable != null) {
      Disposer.dispose(currentDisposable);
    }
    currentDisposable = Disposer.newDisposable(this, "TabbedWelcomeScreen tabs");
    for (WelcomeTabFactory tabFactory : welcomeTabFactories) {
      if (tabFactory.isApplicable()) {
        for (WelcomeScreenTab alsoTab : tabFactory.createWelcomeTabs(this, currentDisposable)) {
          myLeftSidebar.addRootTab(alsoTab);
        }
      }
    }
    myLeftSidebar.init();
  }

  public void setTabListVisible(boolean visible) {
    leftSidebarHolder.setVisible(visible);
  }

  @ApiStatus.Experimental
  public void selectTab(@NotNull WelcomeScreenTab tab) {
    myLeftSidebar.selectTab(tab);
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public void navigateToTabAndSetMainComponent(@NotNull DefaultWelcomeScreenTab tab, Component component) {
    boolean wasSelected = myLeftSidebar.selectTab(tab);
    if (!wasSelected) return;

    var panel = (JComponent)tab.myAssociatedComponent.getComponent(0);
    panel.removeAll();
    panel.add(component, BorderLayout.CENTER);
    revalidate();
    repaint();
    leftSidebarHolder.setVisible(false);
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
