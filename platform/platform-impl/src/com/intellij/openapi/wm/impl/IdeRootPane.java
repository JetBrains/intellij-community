package com.intellij.openapi.wm.impl;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CustomizeUIAction;
import com.intellij.ide.actions.ViewToolbarAction;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.StatusBarCustomComponentFactory;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreen;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made public and non-final for Fabrique
public class IdeRootPane extends JRootPane{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.IdeRootPane");

  /**
   * Toolbar and status bar.
   */
  private JComponent myToolbar;
  private StatusBarImpl myStatusBar;

  private final JPanel myNorthPanel = new JPanel(new GridBagLayout());
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

  IdeRootPane(ActionManager actionManager, UISettings uiSettings, DataManager dataManager, KeymapManager keymapManager,
              final Application application, final String[] commandLineArgs){
    myActionManager = actionManager;
    myUISettings = uiSettings;

    updateToolbar();
    myContentPane.add(myNorthPanel, BorderLayout.NORTH);

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

    myStatusBar.setCustomComponentsFactory(Arrays.asList(StatusBarCustomComponentFactory.EP_NAME.getExtensions()));

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
    myNorthPanel.add(myToolbar, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0,0));
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
    myStatusBar = new StatusBarImpl(myUISettings);
  }

  @Nullable
  final StatusBarEx getStatusBar() {
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
      northComponent.installComponent(project, myNorthPanel);
      northComponent.uiSettingsChanged(myUISettings);
    }
  }

  public void deinstallNorthComponents(){
    for (IdeRootPaneNorthExtension northComponent : myNorthComponents) {
      northComponent.deinstallComponent(myNorthPanel);
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
