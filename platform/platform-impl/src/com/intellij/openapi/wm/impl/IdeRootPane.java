// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.actions.CustomizeUIAction;
import com.intellij.ide.actions.ViewToolbarAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MainFrameHeader;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.MemoryUsagePanel;
import com.intellij.ui.*;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IdeRootPane extends JRootPane implements UISettingsListener, Disposable {
  /**
   * Toolbar and status bar.
   */
  private JComponent myToolbar;
  private IdeStatusBarImpl myStatusBar;
  private boolean myStatusBarDisposed;

  private final JBBox myNorthPanel = JBBox.createVerticalBox();
  private final List<IdeRootPaneNorthExtension> myNorthComponents = new ArrayList<>();

  /**
   * Current {@code ToolWindowsPane}. If there is no such pane then this field is null.
   */
  private ToolWindowsPane myToolWindowsPane;
  private JBPanel<?> myContentPane;

  private final boolean myGlassPaneInitialized;

  private MemoryUsagePanel myMemoryWidget;

  private boolean myFullScreen;

  private MainFrameHeader myCustomFrameTitlePane;
  private final boolean myDecoratedMenu;

  IdeRootPane(@NotNull JFrame frame, @NotNull IdeFrame frameHelper) {
    if (SystemInfo.isWindows && (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())) {
      try {
        setWindowDecorationStyle(FRAME);
      }
      catch (Exception e) {
        Logger.getInstance(IdeRootPane.class).error(e);
      }
    }

    myContentPane.add(myNorthPanel, BorderLayout.NORTH);

    // listen to mouse motion events for a11y
    myContentPane.addMouseMotionListener(new MouseMotionAdapter() {
    });

    IdeMenuBar menu = IdeMenuBar.createMenuBar();
    myDecoratedMenu = IdeFrameDecorator.isCustomDecorationActive();

    if (!isDecoratedMenu() && !WindowManagerImpl.isFloatingMenuBarSupported()) {
      setJMenuBar(menu);
    }
    else {
      if (isDecoratedMenu()) {
        JdkEx.setHasCustomDecoration(frame);

        myCustomFrameTitlePane = CustomHeader.createMainFrameHeader(frame);
        getLayeredPane().add(myCustomFrameTitlePane, JLayeredPane.DEFAULT_LAYER - 2);
        menu.setVisible(false);
      }

      if (WindowManagerImpl.isFloatingMenuBarSupported()) {
        menuBar = menu;
        getLayeredPane().add(menuBar, new Integer(JLayeredPane.DEFAULT_LAYER - 1));
      }

      addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN, __ -> updateScreenState(frameHelper));
      updateScreenState(frameHelper);
    }

    IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(this, true);
    setGlassPane(glassPane);
    myGlassPaneInitialized = true;
    UIUtil.decorateWindowHeader(this);
    glassPane.setVisible(false);
    setBorder(UIManager.getBorder("Window.border"));

    UIUtil.setCustomTitleBar(frame, this, runnable -> Disposer.register(this, () -> runnable.run()));

    updateMainMenuVisibility();
  }

  public void init(@NotNull ProjectFrameHelper frame) {
    createStatusBar(frame);
  }

  private void updateScreenState(@NotNull IdeFrame helper) {
    myFullScreen = helper.isInFullScreen();

    if (isDecoratedMenu()) {
      JMenuBar bar = getJMenuBar();
      if (bar != null) {
        bar.setVisible(myFullScreen);
      }

      if (myCustomFrameTitlePane != null) {
        myCustomFrameTitlePane.setVisible(!myFullScreen);
      }
    }
  }

  @Override
  protected LayoutManager createRootLayout() {
    return WindowManagerImpl.isFloatingMenuBarSupported() ? new MyRootLayout() : super.createRootLayout();
  }

  @Override
  public void setGlassPane(final Component glass) {
    if (myGlassPaneInitialized) throw new IllegalStateException("Setting of glass pane for IdeFrame is prohibited");
    super.setGlassPane(glass);
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  @Override
  public final void addNotify() {
    super.addNotify();
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  @Override
  public final void removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      if (!myStatusBarDisposed) {
        myStatusBarDisposed = true;
        Disposer.dispose(myStatusBar);
      }
      removeToolbar();
      setJMenuBar(null);
      if (myCustomFrameTitlePane != null) {
        layeredPane.remove(myCustomFrameTitlePane);
        Disposer.dispose(myCustomFrameTitlePane);
      }
    }
    super.removeNotify();
  }

  /**
   * Sets current tool windows pane (panel where all tool windows are located).
   * If {@code toolWindowsPane} is {@code null} then the method just removes
   * the current tool windows pane.
   */
  final void setToolWindowsPane(@Nullable final ToolWindowsPane toolWindowsPane) {
    final JComponent contentPane = (JComponent)getContentPane();
    if (myToolWindowsPane != null) {
      contentPane.remove(myToolWindowsPane);
    }

    myToolWindowsPane = toolWindowsPane;
    if (myToolWindowsPane != null) {
      contentPane.add(myToolWindowsPane, BorderLayout.CENTER);
    }

    contentPane.revalidate();
  }

  @Override
  protected JLayeredPane createLayeredPane() {
    JLayeredPane p = new JBLayeredPane();
    p.setName(getName() + ".layeredPane");
    return p;
  }

  @Override
  protected final Container createContentPane() {
    myContentPane = new JBPanel<>(new BorderLayout());
    myContentPane.setBackground(IdeBackgroundUtil.getIdeBackgroundColor());
    return myContentPane;
  }

  void updateToolbar() {
    removeToolbar();
    myToolbar = createToolbar();
    myNorthPanel.add(myToolbar, 0);
    updateToolbarVisibility();
    myContentPane.revalidate();
  }

  private void removeToolbar() {
    if (myToolbar != null) {
      myNorthPanel.remove(myToolbar);
      myToolbar = null;
    }
  }

  void updateNorthComponents() {
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      northComponent.revalidate();
    }
    myContentPane.revalidate();
  }

  void updateMainMenuActions() {
    ((IdeMenuBar)menuBar).updateMenuActions();
    menuBar.repaint();
  }

  private static JComponent createToolbar() {
    ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_TOOLBAR);
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    ActionToolbar toolBar = actionManager.createActionToolbar(ActionPlaces.MAIN_TOOLBAR, Objects.requireNonNull(group), true);
    toolBar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    DefaultActionGroup menuGroup = new DefaultActionGroup();
    menuGroup.add(new ViewToolbarAction());
    menuGroup.add(new CustomizeUIAction());
    PopupHandler.installUnknownPopupHandler(toolBar.getComponent(), menuGroup, actionManager);

    return toolBar.getComponent();
  }

  private void createStatusBar(@NotNull IdeFrame frame) {
    myStatusBar = new IdeStatusBarImpl(frame);
    Disposer.register(this, myStatusBar);

    setMemoryIndicatorVisible(UISettings.getInstance().getShowMemoryIndicator());
    myStatusBar.addWidget(new IdeMessagePanel(frame, MessagePool.getInstance()), StatusBar.Anchors.before(MemoryUsagePanel.WIDGET_ID));

    updateStatusBarVisibility();
    myContentPane.add(myStatusBar, BorderLayout.SOUTH);
  }

  private void setMemoryIndicatorVisible(boolean visible) {
    if (myMemoryWidget == null) {
      if (!visible) {
        myStatusBar.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 6));
        return;
      }

      myMemoryWidget = new MemoryUsagePanel();
      myStatusBar.addWidget(myMemoryWidget);
    }

    myMemoryWidget.setShowing(visible);
    myStatusBar.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, visible ? 0 : 6));
  }

  @Nullable
  final IdeStatusBarImpl getStatusBar() {
    return myStatusBar;
  }

  public int getStatusBarHeight() {
    IdeStatusBarImpl statusBar = myStatusBar;
    return (statusBar != null && statusBar.isVisible()) ? statusBar.getHeight() : 0;
  }

  private void updateToolbarVisibility() {
    UISettings uiSettings = UISettings.getShadowInstance();
    myToolbar.setVisible(uiSettings.getShowMainToolbar() && !uiSettings.getPresentationMode());
  }

  private void updateStatusBarVisibility() {
    UISettings uiSettings = UISettings.getShadowInstance();
    myStatusBar.setVisible(uiSettings.getShowStatusBar() && !uiSettings.getPresentationMode());
  }

  private void updateMainMenuVisibility() {
    UISettings uiSettings = UISettings.getShadowInstance();
    if (uiSettings.getPresentationMode() || IdeFrameDecorator.isCustomDecorationActive()) {
      return;
    }

    final boolean globalMenuVisible = SystemInfo.isLinux && GlobalMenuLinux.isPresented();
    // don't show swing-menu when global (system) menu presented
    final boolean visible = SystemInfo.isMacSystemMenu || !globalMenuVisible && uiSettings.getShowMainMenu();
    if (visible != menuBar.isVisible()) {
      menuBar.setVisible(visible);
    }
  }

  void installNorthComponents(final Project project) {
    if (myCustomFrameTitlePane != null) {
      myCustomFrameTitlePane.setProject(project);
    }

    ContainerUtil.addAll(myNorthComponents, IdeRootPaneNorthExtension.EP_NAME.getExtensions(project));
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      myNorthPanel.add(northComponent.getComponent());
      northComponent.uiSettingsChanged(UISettings.getShadowInstance());
    }
  }

  void deinstallNorthComponents() {
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      myNorthPanel.remove(northComponent.getComponent());
      Disposer.dispose(northComponent);
    }
    myNorthComponents.clear();
  }

  @Nullable
  public IdeRootPaneNorthExtension findByName(@NotNull String name) {
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      if (northComponent.getKey().equals(name)) {
        return northComponent;
      }
    }
    return null;
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    UIUtil.decorateWindowHeader(this);
    setMemoryIndicatorVisible(uiSettings.getShowMemoryIndicator());
    updateToolbarVisibility();
    updateStatusBarVisibility();
    updateMainMenuVisibility();
    for (IdeRootPaneNorthExtension component : myNorthComponents) {
      component.uiSettingsChanged(uiSettings);
    }

    IdeFrameImpl frame = ComponentUtil.getParentOfType(IdeFrameImpl.class, this);
    if (frame == null) {
      return;
    }

    frame.setBackground(UIUtil.getPanelBackground());

    BalloonLayout layout = frame.getBalloonLayout();
    if (layout instanceof BalloonLayoutImpl) {
      ((BalloonLayoutImpl)layout).queueRelayout();
    }
  }

  @Override
  public void dispose() {
  }

  private class MyRootLayout extends RootLayout {
    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension rd;
      Insets i = getInsets();

      if (contentPane != null) {
        rd = contentPane.getPreferredSize();
      }
      else {
        rd = parent.getSize();
      }

      Dimension dimension;
      if (myCustomFrameTitlePane != null && myCustomFrameTitlePane.isVisible()) {
        dimension = myCustomFrameTitlePane.getPreferredSize();
      }
      else {
        dimension = JBUI.emptySize();
      }

      Dimension mbd;
      if (menuBar != null && menuBar.isVisible() && !myFullScreen && !isDecoratedMenu()) {
        mbd = menuBar.getPreferredSize();
      }
      else {
        mbd = JBUI.emptySize();
      }
      return new Dimension(Math.max(rd.width, mbd.width) + i.left + i.right + dimension.width,
                           rd.height + mbd.height + i.top + i.bottom + dimension.height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      Dimension rd;
      Insets i = getInsets();
      if (contentPane != null) {
        rd = contentPane.getMinimumSize();
      }
      else {
        rd = parent.getSize();
      }

      Dimension dimension;
      if (isDecoratedMenu() && myCustomFrameTitlePane != null && myCustomFrameTitlePane.isVisible()) {
        dimension = myCustomFrameTitlePane.getPreferredSize();
      }
      else {
        dimension = JBUI.emptySize();
      }

      Dimension mbd;
      if (menuBar != null && menuBar.isVisible() && !myFullScreen && !isDecoratedMenu()) {
        mbd = menuBar.getMinimumSize();
      }
      else {
        mbd = JBUI.emptySize();
      }
      return new Dimension(Math.max(rd.width, mbd.width) + i.left + i.right + dimension.width,
                           rd.height + mbd.height + i.top + i.bottom + dimension.height);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
      Dimension mbd;
      Insets i = getInsets();
      if (menuBar != null && menuBar.isVisible() && !myFullScreen && !isDecoratedMenu()) {
        mbd = menuBar.getMaximumSize();
      }
      else {
        mbd = JBUI.emptySize();
      }

      Dimension dimension;
      if (isDecoratedMenu() && myCustomFrameTitlePane != null && myCustomFrameTitlePane.isVisible()) {
        dimension = myCustomFrameTitlePane.getPreferredSize();
      }
      else {
        dimension = JBUI.emptySize();
      }

      Dimension rd;
      if (contentPane != null) {
        rd = contentPane.getMaximumSize();
      }
      else {
        rd = new Dimension(Integer.MAX_VALUE,
                           Integer.MAX_VALUE - i.top - i.bottom - mbd.height - 1);
      }
      return new Dimension(Math.min(rd.width, mbd.width) + i.left + i.right + dimension.width,
                           rd.height + mbd.height + i.top + i.bottom + dimension.height);
    }

    @Override
    public void layoutContainer(Container parent) {
      Rectangle b = parent.getBounds();
      Insets i = getInsets();
      int w = b.width - i.right - i.left;
      int h = b.height - i.top - i.bottom;

      if (layeredPane != null) {
        layeredPane.setBounds(i.left, i.top, w, h);
      }
      if (glassPane != null) {
        glassPane.setBounds(i.left, i.top, w, h);
      }
      int contentY = 0;
      if (menuBar != null && menuBar.isVisible()) {
        Dimension mbd = menuBar.getPreferredSize();
        menuBar.setBounds(0, 0, w, mbd.height);
        if (!myFullScreen && !isDecoratedMenu()) {
          contentY += mbd.height;
        }
      }

      if (myCustomFrameTitlePane != null && myCustomFrameTitlePane.isVisible()) {
        Dimension tpd = myCustomFrameTitlePane.getPreferredSize();
        if (tpd != null) {
          int tpHeight = tpd.height;
          myCustomFrameTitlePane.setBounds(0, 0, w, tpHeight);
          contentY += tpHeight;
        }
      }

      if (contentPane != null) {
        contentPane.setBounds(0, contentY, w, h - contentY);
      }
    }
  }

  private boolean isDecoratedMenu() {
    return IdeFrameDecorator.isCustomDecorationActive() && myDecoratedMenu;
  }
}
