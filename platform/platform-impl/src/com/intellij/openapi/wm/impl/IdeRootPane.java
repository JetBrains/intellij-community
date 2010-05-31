/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.diagnostic.*;
import com.intellij.ide.*;
import com.intellij.ide.actions.*;
import com.intellij.ide.plugins.*;
import com.intellij.ide.ui.*;
import com.intellij.ide.ui.customization.*;
import com.intellij.notification.impl.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.keymap.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.status.*;
import com.intellij.openapi.wm.impl.welcomeScreen.*;
import com.intellij.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made public and non-final for Fabrique
public class IdeRootPane extends JRootPane implements UISettingsListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.IdeRootPane");

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
  private JPanel myContentPane;
  private final ActionManager myActionManager;
  private final UISettings myUISettings;

  private static Component myWelcomePane;
  private final boolean myGlassPaneInitialized;
  private final IdeGlassPaneImpl myGlassPane;

  private final Application myApplication;
  private MemoryUsagePanel myMemoryWidget;
  private StatusBarCustomComponentFactory[] myStatusBarCustomComponentFactories;

  IdeRootPane(ActionManager actionManager, UISettings uiSettings, DataManager dataManager, KeymapManager keymapManager,
              final Application application, final String[] commandLineArgs){
    myActionManager = actionManager;
    myUISettings = uiSettings;

    updateToolbar();
    myContentPane.add(myNorthPanel, BorderLayout.NORTH);

    myStatusBarCustomComponentFactories = application.getExtensions(StatusBarCustomComponentFactory.EP_NAME);

    createStatusBar();
    updateStatusBarVisibility();

    myContentPane.add(myStatusBar, BorderLayout.SOUTH);

    myUISettingsListener=new MyUISettingsListenerImpl();
    setJMenuBar(new IdeMenuBar(myActionManager, dataManager, keymapManager));

    final Ref<Boolean> willOpenProject = new Ref<Boolean>(Boolean.FALSE);
    final AppLifecycleListener lifecyclePublisher = application.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
    lifecyclePublisher.appFrameCreated(commandLineArgs, willOpenProject);
    LOG.info("App initialization took " + (System.nanoTime() - PluginManager.startupStart) / 1000000 + " ms");
    PluginManager.dumpPluginClassStatistics();
    if (!willOpenProject.get()) {
      myWelcomePane = WelcomeScreen.createWelcomePanel();
      myContentPane.add(myWelcomePane);
      lifecyclePublisher.welcomeScreenDisplayed();
    }

    myGlassPane = new IdeGlassPaneImpl(this);
    setGlassPane(myGlassPane);
    myGlassPaneInitialized = true;

    myGlassPane.setVisible(false);
    myApplication = application;
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
    myUISettings.addUISettingsListener(myUISettingsListener);
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public final void removeNotify(){
    myUISettings.removeUISettingsListener(myUISettingsListener);
    super.removeNotify();
  }

  /**
   * Sets current tool windows pane (panel where all tool windows are located).
   * If <code>toolWindowsPane</code> is <code>null</code> then the method just removes
   * the current tool windows pane.
   */
  final void setToolWindowsPane(final ToolWindowsPane toolWindowsPane) {
    final JComponent contentPane = (JComponent)getContentPane();
    if(myToolWindowsPane != null){
      contentPane.remove(myToolWindowsPane);
    }

    if (myWelcomePane != null) {
      contentPane.remove(myWelcomePane);
      myWelcomePane = null;
    }

    myToolWindowsPane = toolWindowsPane;
    if(myToolWindowsPane != null) {
      contentPane.add(myToolWindowsPane,BorderLayout.CENTER);
    }
    else if (!myApplication.isDisposeInProgress()) {
      myWelcomePane = WelcomeScreen.createWelcomePanel();
      contentPane.add(myWelcomePane);
    }

    contentPane.revalidate();
  }

  protected final Container createContentPane(){
    myContentPane = new JPanel(new BorderLayout());
    myContentPane.setBackground(Color.GRAY);

    return myContentPane;
  }

  void updateToolbar() {
    if (myToolbar != null) {
      myNorthPanel.remove(myToolbar);
    }
    myToolbar = createToolbar();
    myNorthPanel.add(myToolbar);
    updateToolbarVisibility();
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
 
  private void createStatusBar() {
    myUISettings.addUISettingsListener(this);

    myStatusBar = new IdeStatusBarImpl();

    myMemoryWidget = new MemoryUsagePanel();
    myStatusBar.addWidget(myMemoryWidget);
    myStatusBar.addWidget(new IdeNotificationArea(), "before Memory");
    myStatusBar.addWidget(new IdeMessagePanel(MessagePool.getInstance()), "before Memory");

    if (myStatusBarCustomComponentFactories != null) {
      for (final StatusBarCustomComponentFactory componentFactory : myStatusBarCustomComponentFactories) {
        final JComponent c = componentFactory.createComponent(myStatusBar);
        myStatusBar.addWidget(new CustomStatusBarWidget() {
          public JComponent getComponent() {
            return c;
          }

          @NotNull
          public String ID() {
            return c.getClass().getSimpleName();
          }

          public Presentation getPresentation(@NotNull Type type) {
            return null;
          }

          public void install(@NotNull StatusBar statusBar) {
          }

          public void dispose() {
            componentFactory.disposeComponent(myStatusBar, c);
          }
        }, "before Memory");
      }
    }

    setMemoryIndicatorVisible(myUISettings.SHOW_MEMORY_INDICATOR);
  }

  void setMemoryIndicatorVisible(final boolean visible) {
    if (myMemoryWidget != null) {
      myMemoryWidget.setShowing(visible);
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
    myNorthComponents.addAll(Arrays.asList(Extensions.getExtensions(IdeRootPaneNorthExtension.EP_NAME, project)));
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
}
