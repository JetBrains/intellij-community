/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.MemoryUsagePanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made public and non-final for Fabrique
public class IdeRootPane extends JRootPane implements UISettingsListener {

  /**
   * Toolbar and status bar.
   */
  private JComponent myToolbar;
  private IdeStatusBarImpl myStatusBar;

  private final Box myNorthPanel = Box.createVerticalBox();
  private final List<IdeRootPaneNorthExtension> myNorthComponents = new ArrayList<IdeRootPaneNorthExtension>();

  /**
   * Current <code>ToolWindowsPane</code>. If there is no such pane then this field is null.
   */
  private ToolWindowsPane myToolWindowsPane;
  private final MyUISettingsListenerImpl myUISettingsListener;
  private JBPanel myContentPane;
  private final ActionManager myActionManager;
  private final UISettings myUISettings;

  private final boolean myGlassPaneInitialized;
  private final IdeGlassPaneImpl myGlassPane;

  private final Application myApplication;
  private MemoryUsagePanel myMemoryWidget;
  private final StatusBarCustomComponentFactory[] myStatusBarCustomComponentFactories;
  private final Disposable myDisposable= Disposer.newDisposable();

  private static final Icon BG = IconLoader.getIcon("/frame_background.png");
  private boolean myFullScreen;

  public IdeRootPane(ActionManagerEx actionManager, UISettings uiSettings, DataManager dataManager,
              final Application application, final IdeFrame frame){
    myActionManager = actionManager;
    myUISettings = uiSettings;

    updateToolbar();
    myContentPane.add(myNorthPanel, BorderLayout.NORTH);

    myStatusBarCustomComponentFactories = application.getExtensions(StatusBarCustomComponentFactory.EP_NAME);
    myApplication = application;

    createStatusBar(frame);

    updateStatusBarVisibility();

    myContentPane.add(myStatusBar, BorderLayout.SOUTH);

    myUISettingsListener=new MyUISettingsListenerImpl();
    if (SystemInfo.isWindows) {
      menuBar = new IdeMenuBar(actionManager, dataManager);
      getLayeredPane().add(menuBar, new Integer(JLayeredPane.DEFAULT_LAYER - 1));
      if (frame instanceof IdeFrameImpl) {
        final PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getNewValue() == null) {//fullscreen state has been just changed
              myFullScreen = ((IdeFrameImpl)frame).isInFullScreen();
            }
          }
        };
        addPropertyChangeListener(ScreenUtil.DISPOSE_TEMPORARY, propertyChangeListener);
      }
    }
    else {
      setJMenuBar(new IdeMenuBar(actionManager, dataManager));
    }

    myGlassPane = new IdeGlassPaneImpl(this);
    setGlassPane(myGlassPane);
    myGlassPaneInitialized = true;

    myGlassPane.setVisible(false);
    Disposer.register(application, myDisposable);
  }

  @Override
  protected LayoutManager createRootLayout() {
    return SystemInfo.isWindows ? new MyRootLayout() : super.createRootLayout();
  }


  public void setGlassPane(final Component glass) {
    if (myGlassPaneInitialized) throw new IllegalStateException("Setting of glass pane for IdeFrame is prohibited");
    super.setGlassPane(glass);
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public final void addNotify(){
    super.addNotify();
    myUISettings.addUISettingsListener(myUISettingsListener, myDisposable);
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public final void removeNotify(){
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

  protected final Container createContentPane(){
    myContentPane = new JBPanel(new BorderLayout()){
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (UIUtil.isUnderDarcula()) {
          String icon = ApplicationInfoEx.getInstanceEx().getEditorBackgroundImageUrl();
          if (icon != null) IconUtil.paintInCenterOf(this, g, IconLoader.getIcon(icon));
        }
      }
    };
    if (UIUtil.isUnderDarcula()) {
      myContentPane.setBackgroundImage(BG);
    }
    myContentPane.setBackground(JBColor.GRAY);

    return myContentPane;
  }

  void updateToolbar() {
    if (myToolbar != null) {
      myNorthPanel.remove(myToolbar);
    }
    myToolbar = createToolbar();
    myNorthPanel.add(myToolbar, 0);
    updateToolbarVisibility();
    myContentPane.revalidate();
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
    myUISettings.addUISettingsListener(this, myApplication);

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
    myStatusBar.addWidget(new IdeMessagePanel(MessagePool.getInstance()), "before " + MemoryUsagePanel.WIDGET_ID);

    setMemoryIndicatorVisible(myUISettings.SHOW_MEMORY_INDICATOR);
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

  private void updateToolbarVisibility(){
    myToolbar.setVisible(myUISettings.SHOW_MAIN_TOOLBAR);
  }

  private void updateStatusBarVisibility(){
    myStatusBar.setVisible(myUISettings.SHOW_STATUS_BAR);
  }

  public void installNorthComponents(final Project project) {
    ContainerUtil.addAll(myNorthComponents, Extensions.getExtensions(IdeRootPaneNorthExtension.EP_NAME, project));
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      myNorthPanel.add(northComponent.getComponent());
      northComponent.uiSettingsChanged(myUISettings);
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
  }

  private final class MyUISettingsListenerImpl implements UISettingsListener{
    public final void uiSettingsChanged(final UISettings source){
      updateToolbarVisibility();
      updateStatusBarVisibility();
      for (IdeRootPaneNorthExtension component : myNorthComponents) {
        component.uiSettingsChanged(source);
      }
    }
  }

  public boolean isOptimizedDrawingEnabled() {
    return !myGlassPane.hasPainters() && myGlassPane.getComponentCount() == 0;
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
      if (menuBar != null && menuBar.isVisible() && !myFullScreen) {
        mbd = menuBar.getPreferredSize();
      }
      else {
        mbd = new Dimension(0, 0);
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
        mbd = new Dimension(0, 0);
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
        mbd = new Dimension(0, 0);
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
}
