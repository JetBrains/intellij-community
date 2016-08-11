/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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

  private final Box myNorthPanel = Box.createVerticalBox();
  private final List<IdeRootPaneNorthExtension> myNorthComponents = new ArrayList<>();

  /**
   * Current <code>ToolWindowsPane</code>. If there is no such pane then this field is null.
   */
  private ToolWindowsPane myToolWindowsPane;
  private JBPanel myContentPane;
  private final ActionManager myActionManager;

  private final boolean myGlassPaneInitialized;
  private final IdeGlassPaneImpl myGlassPane;

  private final Application myApplication;
  private MemoryUsagePanel myMemoryWidget;
  private final StatusBarCustomComponentFactory[] myStatusBarCustomComponentFactories;

  private boolean myFullScreen;

  public IdeRootPane(ActionManagerEx actionManager, DataManager dataManager, Application application, final IdeFrame frame) {
    if (SystemInfo.isWindows && (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) && frame instanceof IdeFrameImpl) {
      //setUI(DarculaRootPaneUI.createUI(this));
      setWindowDecorationStyle(FRAME);
    }
    myActionManager = actionManager;

    myContentPane.add(myNorthPanel, BorderLayout.NORTH);

    myContentPane.addMouseMotionListener(new MouseMotionAdapter() {}); // listen to mouse motion events for a11y

    myStatusBarCustomComponentFactories = application.getExtensions(StatusBarCustomComponentFactory.EP_NAME);
    myApplication = application;

    createStatusBar(frame);

    updateStatusBarVisibility();
    updateToolbar();

    myContentPane.add(myStatusBar, BorderLayout.SOUTH);

    if (WindowManagerImpl.isFloatingMenuBarSupported()) {
      if (!isDecoratedMenu()) {
        menuBar = new IdeMenuBar(actionManager, dataManager);
        getLayeredPane().add(menuBar, new Integer(JLayeredPane.DEFAULT_LAYER - 1));
        if (frame instanceof IdeFrameEx) {
          addPropertyChangeListener(WindowManagerImpl.FULL_SCREEN, new PropertyChangeListener() {
            @Override public void propertyChange(PropertyChangeEvent evt) {
              myFullScreen = ((IdeFrameEx)frame).isInFullScreen();
            }
          });
        }
      }
    }
    else {
      setJMenuBar(new IdeMenuBar(actionManager, dataManager));
    }

    myGlassPane = new IdeGlassPaneImpl(this, true);
    setGlassPane(myGlassPane);
    myGlassPaneInitialized = true;

    myGlassPane.setVisible(false);
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
  public final void addNotify(){
    super.addNotify();
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
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
   * If <code>toolWindowsPane</code> is <code>null</code> then the method just removes
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

  protected JLayeredPane createLayeredPane() {
    JLayeredPane p = new JBLayeredPane();
    p.setName(this.getName()+".layeredPane");
    return p;
  }

  @Override
  public void setLayout(LayoutManager mgr) {
    //First time mgr comes from createRootLayout(), it's OK. But then Alloy spoils it and breaks FullScreen mode under Windows
    if (getLayout() != null && UIUtil.isUnderAlloyLookAndFeel()) return;
    super.setLayout(mgr);
  }

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
          public JComponent getComponent() {
            return c;
          }

          @NotNull
          public String ID() {
            return c.getClass().getSimpleName();
          }

          public WidgetPresentation getPresentation(@NotNull PlatformType type) {
            return null;
          }

          public void install(@NotNull StatusBar statusBar) {
          }

          public void dispose() {
            componentFactory.disposeComponent(myStatusBar, c);
          }
        }, "before " + MemoryUsagePanel.WIDGET_ID);
      }
    }

    myStatusBar.addWidget(myMemoryWidget);
    myStatusBar.addWidget(new IdeMessagePanel(frame, MessagePool.getInstance()), "before " + MemoryUsagePanel.WIDGET_ID);

    setMemoryIndicatorVisible(UISettings.getInstance().SHOW_MEMORY_INDICATOR);
  }

  void setMemoryIndicatorVisible(final boolean visible) {
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
    myToolbar.setVisible(UISettings.getInstance().SHOW_MAIN_TOOLBAR && !UISettings.getInstance().PRESENTATION_MODE);
  }

  private void updateStatusBarVisibility(){
    myStatusBar.setVisible(UISettings.getInstance().SHOW_STATUS_BAR && !UISettings.getInstance().PRESENTATION_MODE);
  }

  public void installNorthComponents(final Project project) {
    ContainerUtil.addAll(myNorthComponents, Extensions.getExtensions(IdeRootPaneNorthExtension.EP_NAME, project));
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      myNorthPanel.add(northComponent.getComponent());
      northComponent.uiSettingsChanged(UISettings.getInstance());
    }
  }

  public void deinstallNorthComponents(){
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

  public void uiSettingsChanged(UISettings source) {
    setMemoryIndicatorVisible(source.SHOW_MEMORY_INDICATOR);
    updateToolbarVisibility();
    updateStatusBarVisibility();
    for (IdeRootPaneNorthExtension component : myNorthComponents) {
      component.uiSettingsChanged(source);
    }
    IdeFrame frame = UIUtil.getParentOfType(IdeFrame.class, this);
    BalloonLayout layout = frame != null ? frame.getBalloonLayout() : null;
    if (layout instanceof BalloonLayoutImpl) ((BalloonLayoutImpl)layout).queueRelayout();
  }

  public ToolWindowsPane getToolWindowsPane() {
    return myToolWindowsPane;
  }

  private class MyRootLayout extends RootLayout {
    public Dimension preferredLayoutSize(Container parent) {
      Dimension rd, mbd;
      Insets i = getInsets();

      if (contentPane != null) {
        rd = contentPane.getPreferredSize();
      }
      else {
        rd = parent.getSize();
      }
      if (menuBar != null && menuBar.isVisible() && !myFullScreen && !isDecoratedMenu()) {
        mbd = menuBar.getPreferredSize();
      }
      else {
        mbd = JBUI.emptySize();
      }
      return new Dimension(Math.max(rd.width, mbd.width) + i.left + i.right,
                           rd.height + mbd.height + i.top + i.bottom);
    }

    public Dimension minimumLayoutSize(Container parent) {
      Dimension rd, mbd;
      Insets i = getInsets();
      if (contentPane != null) {
        rd = contentPane.getMinimumSize();
      }
      else {
        rd = parent.getSize();
      }
      if (menuBar != null && menuBar.isVisible() && !myFullScreen) {
        mbd = menuBar.getMinimumSize();
      }
      else {
        mbd = JBUI.emptySize();
      }
      return new Dimension(Math.max(rd.width, mbd.width) + i.left + i.right,
                           rd.height + mbd.height + i.top + i.bottom);
    }

    public Dimension maximumLayoutSize(Container target) {
      Dimension rd, mbd;
      Insets i = getInsets();
      if (menuBar != null && menuBar.isVisible() && !myFullScreen) {
        mbd = menuBar.getMaximumSize();
      }
      else {
        mbd = JBUI.emptySize();
      }
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

    public void layoutContainer(Container parent) {
      Rectangle b = parent.getBounds();
      Insets i = getInsets();
      int contentY = 0;
      int w = b.width - i.right - i.left;
      int h = b.height - i.top - i.bottom;

      if (layeredPane != null) {
        layeredPane.setBounds(i.left, i.top, w, h);
      }
      if (glassPane != null) {
        glassPane.setBounds(i.left, i.top, w, h);
      }
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

  public static boolean isFrameDecorated() {
    return SystemInfo.isWindows && Registry.is("ide.win.frame.decoration");
  }

  public boolean isDecoratedMenu() {
    return getUI() instanceof DarculaRootPaneUI && isFrameDecorated();
  }
}
