// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.diagnostic.MessagePool;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CustomizeUIAction;
import com.intellij.ide.actions.ViewToolbarAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.laf.darcula.ui.DarculaRootPaneUI;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.MemoryUsagePanel;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class IdeRootPane extends JRootPane implements UISettingsListener {
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
  private JBPanel myContentPane;
  private final ActionManager myActionManager;

  private final boolean myGlassPaneInitialized;

  private MemoryUsagePanel myMemoryWidget;
  private final StatusBarCustomComponentFactory[] myStatusBarCustomComponentFactories;

  private boolean myFullScreen;

  IdeRootPane(ActionManagerEx actionManager, DataManager dataManager, Application application, final IdeFrame frame) {
    if (SystemInfo.isWindows && (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) && frame instanceof IdeFrameImpl) {
      //setUI(DarculaRootPaneUI.createUI(this));
      try {
        setWindowDecorationStyle(FRAME);
      }
      catch (Exception e) {
        Logger.getInstance(IdeRootPane.class).error(e);
      }
    }
    myActionManager = actionManager;

    myContentPane.add(myNorthPanel, BorderLayout.NORTH);

    myContentPane.addMouseMotionListener(new MouseMotionAdapter() {}); // listen to mouse motion events for a11y

    myStatusBarCustomComponentFactories = application.getExtensions(StatusBarCustomComponentFactory.EP_NAME);

    createStatusBar(frame);

    updateStatusBarVisibility();

    myContentPane.add(myStatusBar, BorderLayout.SOUTH);

    if (WindowManagerImpl.isFloatingMenuBarSupported()) {
      if (!isDecoratedMenu()) {
        menuBar = new IdeMenuBar(actionManager, dataManager);
        getLayeredPane().add(menuBar, new Integer(JLayeredPane.DEFAULT_LAYER - 1));
        if (frame instanceof IdeFrameEx) {
          addPropertyChangeListener(WindowManagerImpl.FULL_SCREEN, __ -> myFullScreen = ((IdeFrameEx)frame).isInFullScreen());
        }
      }
    }
    else {
      setJMenuBar(new IdeMenuBar(actionManager, dataManager));
    }

    IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(this, true);
    setGlassPane(glassPane);
    myGlassPaneInitialized = true;
    UIUtil.decorateFrame(this);
    glassPane.setVisible(false);
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
  public final void addNotify(){
    super.addNotify();
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  @Override
  public final void removeNotify(){
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      if (!myStatusBarDisposed) {
        myStatusBarDisposed = true;
        Disposer.dispose(myStatusBar);
      }
      removeToolbar();
      setJMenuBar(null);
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
    if(myToolWindowsPane != null){
      contentPane.remove(myToolWindowsPane);
    }

    myToolWindowsPane = toolWindowsPane;
    if(myToolWindowsPane != null) {
      contentPane.add(myToolWindowsPane,BorderLayout.CENTER);
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
  protected final Container createContentPane(){
    return myContentPane = new IdePanePanel(new BorderLayout());
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

  void updateMainMenuActions(){
    ((IdeMenuBar)menuBar).updateMenuActions();
    menuBar.repaint();
  }

  private JComponent createToolbar() {
    ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_TOOLBAR);
    final ActionToolbar toolBar= myActionManager.createActionToolbar(
      ActionPlaces.MAIN_TOOLBAR,
      group,
      true
    );
    toolBar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    DefaultActionGroup menuGroup = new DefaultActionGroup();
    menuGroup.add(new ViewToolbarAction());
    menuGroup.add(new CustomizeUIAction());
    PopupHandler.installUnknownPopupHandler(toolBar.getComponent(), menuGroup, myActionManager);

    return toolBar.getComponent();
  }

  private void createStatusBar(IdeFrame frame) {
    myStatusBar = new IdeStatusBarImpl();
    myStatusBar.install(frame);

    myMemoryWidget = new MemoryUsagePanel();

    if (myStatusBarCustomComponentFactories != null) {
      for (final StatusBarCustomComponentFactory<JComponent> componentFactory : myStatusBarCustomComponentFactories) {
        final JComponent c = componentFactory.createComponent(myStatusBar);
        myStatusBar.addWidget(new CustomStatusBarWidget() {
          @Override
          public JComponent getComponent() {
            return c;
          }

          @Override
          @NotNull
          public String ID() {
            return c.getClass().getSimpleName();
          }

          @Override
          public WidgetPresentation getPresentation(@NotNull PlatformType type) {
            return null;
          }

          @Override
          public void install(@NotNull StatusBar statusBar) {
          }

          @Override
          public void dispose() {
            componentFactory.disposeComponent(myStatusBar, c);
          }
        }, "before " + MemoryUsagePanel.WIDGET_ID);
      }
    }

    myStatusBar.addWidget(myMemoryWidget);
    myStatusBar.addWidget(new IdeMessagePanel(frame, MessagePool.getInstance()), "before " + MemoryUsagePanel.WIDGET_ID);

    setMemoryIndicatorVisible(UISettings.getInstance().getShowMemoryIndicator());
  }

  private void setMemoryIndicatorVisible(final boolean visible) {
    if (myMemoryWidget != null) {
      myMemoryWidget.setShowing(visible);
      if (!SystemInfo.isMac) {
        myStatusBar.setBorder(BorderFactory.createEmptyBorder(1, 4, 0, visible ? 0 : 2));
      }
    }
  }

  @Nullable
  final StatusBar getStatusBar() {
    return myStatusBar;
  }

  public int getStatusBarHeight() {
    return myStatusBar.isVisible() ? myStatusBar.getHeight() : 0;
  }

  private void updateToolbarVisibility(){
    myToolbar.setVisible(UISettings.getInstance().getShowMainToolbar() && !UISettings.getInstance().getPresentationMode());
  }

  private void updateStatusBarVisibility(){
    myStatusBar.setVisible(UISettings.getInstance().getShowStatusBar() && !UISettings.getInstance().getPresentationMode());
  }

  void installNorthComponents(final Project project) {
    ContainerUtil.addAll(myNorthComponents, Extensions.getExtensions(IdeRootPaneNorthExtension.EP_NAME, project));
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      myNorthPanel.add(northComponent.getComponent());
      northComponent.uiSettingsChanged(UISettings.getInstance());
    }
  }

  void deinstallNorthComponents(){
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      myNorthPanel.remove(northComponent.getComponent());
      Disposer.dispose(northComponent);
    }
    myNorthComponents.clear();
  }

  public IdeRootPaneNorthExtension findByName(String name) {
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      if (Comparing.strEqual(name, northComponent.getKey())) {
        return northComponent;
      }
    }
    return null;
  }

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    UIUtil.decorateFrame(this);
    setMemoryIndicatorVisible(uiSettings.getShowMemoryIndicator());
    updateToolbarVisibility();
    updateStatusBarVisibility();
    for (IdeRootPaneNorthExtension component : myNorthComponents) {
      component.uiSettingsChanged(uiSettings);
    }
    IdeFrame frame = UIUtil.getParentOfType(IdeFrame.class, this);
    BalloonLayout layout = frame != null ? frame.getBalloonLayout() : null;
    if (layout instanceof BalloonLayoutImpl) ((BalloonLayoutImpl)layout).queueRelayout();
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
      Dimension mbd;
      if (menuBar != null && menuBar.isVisible() && !myFullScreen && !isDecoratedMenu()) {
        mbd = menuBar.getPreferredSize();
      }
      else {
        mbd = JBUI.emptySize();
      }
      return new Dimension(Math.max(rd.width, mbd.width) + i.left + i.right,
                           rd.height + mbd.height + i.top + i.bottom);
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
      Dimension mbd;
      if (menuBar != null && menuBar.isVisible() && !myFullScreen) {
        mbd = menuBar.getMinimumSize();
      }
      else {
        mbd = JBUI.emptySize();
      }
      return new Dimension(Math.max(rd.width, mbd.width) + i.left + i.right,
                           rd.height + mbd.height + i.top + i.bottom);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
      Dimension mbd;
      Insets i = getInsets();
      if (menuBar != null && menuBar.isVisible() && !myFullScreen) {
        mbd = menuBar.getMaximumSize();
      }
      else {
        mbd = JBUI.emptySize();
      }
      Dimension rd;
      if (contentPane != null) {
        rd = contentPane.getMaximumSize();
      }
      else {
        rd = new Dimension(Integer.MAX_VALUE,
                           Integer.MAX_VALUE - i.top - i.bottom - mbd.height - 1);
      }
      return new Dimension(Math.min(rd.width, mbd.width) + i.left + i.right,
                           rd.height + mbd.height + i.top + i.bottom);
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
        if (!myFullScreen) {
          contentY += mbd.height;
        }
      }
      if (contentPane != null) {
        contentPane.setBounds(0, contentY, w, h - contentY);
      }
    }
  }

  static boolean isFrameDecorated() {
    return SystemInfo.isWindows && Registry.is("ide.win.frame.decoration");
  }

  private boolean isDecoratedMenu() {
    return getUI() instanceof DarculaRootPaneUI && isFrameDecorated();
  }
}
