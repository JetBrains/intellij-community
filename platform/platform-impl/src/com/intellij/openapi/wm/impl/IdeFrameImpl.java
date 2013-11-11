/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.status.*;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class IdeFrameImpl extends JFrame implements IdeFrameEx, DataProvider {
  public static final Key<Boolean> SHOULD_OPEN_IN_FULL_SCREEN = Key.create("should.open.in.full.screen");

  private static final String FULL_SCREEN = "FullScreen";

  private static boolean myUpdatingTitle;

  private static String xdgCurrentDesktop = System.getenv("XDG_CURRENT_DESKTOP");

  private String myTitle;
  private String myFileTitle;
  private File myCurrentFile;

  private Project myProject;

  private IdeRootPane myRootPane;
  private final BalloonLayout myBalloonLayout;
  private IdeFrameDecorator myFrameDecorator;
  private boolean myRestoreFullScreen;

  public IdeFrameImpl(ApplicationInfoEx applicationInfoEx,
                      ActionManagerEx actionManager,
                      UISettings uiSettings,
                      DataManager dataManager,
                      Application application) {
    super(applicationInfoEx.getFullApplicationName());
    myRootPane = createRootPane(actionManager, uiSettings, dataManager, application);
    setRootPane(myRootPane);
    setBackground(UIUtil.getPanelBackground());
    AppUIUtil.updateWindowIcon(this);
    final Dimension size = ScreenUtil.getMainScreenBounds().getSize();

    size.width = Math.min(1400, size.width - 20);
    size.height= Math.min(1000, size.height - 40);

    setSize(size);
    setLocationRelativeTo(null);

    LayoutFocusTraversalPolicyExt layoutFocusTraversalPolicy = new LayoutFocusTraversalPolicyExt();
    setFocusTraversalPolicy(layoutFocusTraversalPolicy);

    setupCloseAction();
    new MnemonicHelper().register(this);

    myBalloonLayout = new BalloonLayoutImpl(myRootPane.getLayeredPane(), new Insets(8, 8, 8, 8));

    if (!Registry.is("ide.windowSystem.focusAppOnStartup") && !isThereActiveFrame()) {
      setFocusableWindowState(false);
    }

    // to show window thumbnail under Macs
    // http://lists.apple.com/archives/java-dev/2009/Dec/msg00240.html
    if (SystemInfo.isMac) setIconImage(null);

    MouseGestureManager.getInstance().add(this);

    myFrameDecorator = IdeFrameDecorator.decorate(this);

    addWindowStateListener(new WindowAdapter() {
      @Override
      public void windowStateChanged(WindowEvent e) {
        updateBorder();
      }
    });

    Toolkit.getDefaultToolkit().addPropertyChangeListener("win.xpstyle.themeActive", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateBorder();
      }
    });

    IdeMenuBar.installAppMenuIfNeeded(this);
  }

  private void updateBorder() {
    int state = getExtendedState();
    if (!WindowManager.getInstance().isFullScreenSupportedInCurrentOS() || !SystemInfo.isWindows || myRootPane == null) {
      return;
    }

    myRootPane.setBorder(null);
    boolean isNotClassic = Boolean.parseBoolean(String.valueOf(Toolkit.getDefaultToolkit().getDesktopProperty("win.xpstyle.themeActive")));
    if (isNotClassic && (state & MAXIMIZED_BOTH) != 0) {
      IdeFrame[] projectFrames = WindowManager.getInstance().getAllProjectFrames();
      GraphicsDevice device = ScreenUtil.getScreenDevice(getBounds());

      for (IdeFrame frame : projectFrames) {
        if (frame == this) continue;
        if (((IdeFrameImpl)frame).isInFullScreen() && ScreenUtil.getScreenDevice(((IdeFrameImpl)frame).getBounds()) == device) {
          Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(device.getDefaultConfiguration());
          int mask = SideBorder.NONE;
          if (insets.top != 0) mask |= SideBorder.TOP;
          if (insets.left != 0) mask |= SideBorder.LEFT;
          if (insets.bottom != 0) mask |= SideBorder.BOTTOM;
          if (insets.right != 0) mask |= SideBorder.RIGHT;
          myRootPane.setBorder(new SideBorder(JBColor.BLACK, mask, false, 3));
          break;
        }
      }
    }
  }

  protected IdeRootPane createRootPane(ActionManagerEx actionManager,
                                       UISettings uiSettings,
                                       DataManager dataManager,
                                       Application application) {
    return new IdeRootPane(actionManager, uiSettings, dataManager, application, this);
  }

  @Override
  public Insets getInsets() {
    if (SystemInfo.isMac && isInFullScreen()) {
      return new Insets(0, 0, 0, 0);
    }
    return super.getInsets();
  }

  @Override
  public JComponent getComponent() {
    return getRootPane();
  }

  @Nullable
  public static Window getActiveFrame() {
    for (Frame frame : getFrames()) {
      if (frame.isActive()) return frame;
    }
    return null;
  }

  public static boolean isThereActiveFrame() {
    Frame[] all = Frame.getFrames();
    for (Frame each : all) {
      if (each.isActive()) {
        return true;
      }
    }

    return false;
  }

  @SuppressWarnings({"deprecation", "SSBasedInspection"})
  @Override
  public void show() {
    super.show();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        setFocusableWindowState(true);
      }
    });
  }

  /**
   * This is overridden to get rid of strange Alloy LaF customization of frames. For unknown reason it sets the maxBounds rectangle
   * and it does it plain wrong. Setting bounds to <code>null</code> means default value should be taken from the underlying OS.
   */
  public synchronized void setMaximizedBounds(Rectangle bounds) {
    super.setMaximizedBounds(null);
  }

  private void setupCloseAction() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
      new WindowAdapter() {
        public void windowClosing(final WindowEvent e) {
          if (isTemporaryDisposed())
            return;
          final Application app = ApplicationManager.getApplication();
          app.invokeLater(new DumbAwareRunnable() {
            public void run() {
              if (app.isDisposed()) {
                ApplicationManagerEx.getApplicationEx().exit();
                return;
              }

              final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
              if (openProjects.length > 1 || (openProjects.length == 1 && SystemInfo.isMacSystemMenu)) {
                if (myProject != null && myProject.isOpen()) {
                  ProjectUtil.closeAndDispose(myProject);
                }
                app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).projectFrameClosed();
                WelcomeFrame.showIfNoProjectOpened();
              }
              else {
                ApplicationManagerEx.getApplicationEx().exit();
              }
            }
          }, ModalityState.NON_MODAL);
        }
      }
    );
  }

  public StatusBar getStatusBar() {
    return ((IdeRootPane)getRootPane()).getStatusBar();
  }

  public void setTitle(final String title) {
    if (myUpdatingTitle) {
      super.setTitle(title);
    } else {
      myTitle = title;
    }

    updateTitle();
  }

  public void setFrameTitle(final String text) {
    super.setTitle(text);
  }

  public void setFileTitle(final String fileTitle) {
    setFileTitle(fileTitle, null);
  }

  public void setFileTitle(@Nullable final String fileTitle, @Nullable File file) {
    myFileTitle = fileTitle;
    myCurrentFile = file;
    updateTitle();
  }

  @Override
  public IdeRootPaneNorthExtension getNorthExtension(String key) {
    return myRootPane.findByName(key);
  }

  private void updateTitle() {
    updateTitle(this, myTitle, myFileTitle, myCurrentFile);
  }

  public static void updateTitle(JFrame frame, final String title, final String fileTitle, final File currentFile) {
    if (myUpdatingTitle) return;

    try {
      myUpdatingTitle = true;

      frame.getRootPane().putClientProperty("Window.documentFile", currentFile);

      final String applicationName = ((ApplicationInfoEx)ApplicationInfo.getInstance()).getFullApplicationName();
      final Builder builder = new Builder();
      if (SystemInfo.isMac) {
        builder.append(fileTitle).append(title)
          .append(ProjectManager.getInstance().getOpenProjects().length == 0
                  || ((ApplicationInfoEx)ApplicationInfo.getInstance()).isEAP() && !applicationName.endsWith("SNAPSHOT") ? applicationName : null);
      } else {
        builder.append(title).append(fileTitle).append(applicationName);
      }

      frame.setTitle(builder.sb.toString());
    }
    finally {
      myUpdatingTitle = false;
    }
  }

  public void updateView() {
    ((IdeRootPane)getRootPane()).updateToolbar();
    ((IdeRootPane)getRootPane()).updateMainMenuActions();
    ((IdeRootPane)getRootPane()).updateNorthComponents();
  }

  private static final class Builder {
    public StringBuilder sb = new StringBuilder();

    public Builder append(@Nullable final String s) {
      if (s == null || s.length() == 0) return this;
      if (sb.length() > 0) sb.append(" - ");
      sb.append(s);
      return this;
    }
  }

  public Object getData(final String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      if (myProject != null) {
        return myProject.isInitialized() ? myProject : null;
      }
    }

    if (IdeFrame.KEY.getName().equals(dataId)) {
      return this;
    }

    return null;
  }

  public void setProject(final Project project) {
    if (WindowManager.getInstance().isFullScreenSupportedInCurrentOS() && myProject != project && project != null) {
      myRestoreFullScreen = myProject == null && shouldRestoreFullScreen(project);

      if (myProject != null) {
        storeFullScreenStateIfNeeded(false); // disable for old project
      }
    }

    myProject = project;
    if (project != null) {
      ProjectFrameBounds.getInstance(project);   // make sure the service is initialized and its state will be saved
      if (myRootPane != null) {
        myRootPane.installNorthComponents(project);
        project.getMessageBus().connect().subscribe(StatusBar.Info.TOPIC, myRootPane.getStatusBar());
      }

      installDefaultProjectStatusBarWidgets(myProject);
    }
    else {
      if (myRootPane != null) { //already disposed
        myRootPane.deinstallNorthComponents();
      }
    }

    if (project == null) {
      FocusTrackback.release(this);
    }

    if (isVisible() && myRestoreFullScreen) {
      toggleFullScreen(true);
      myRestoreFullScreen = false;
    }
  }

  @SuppressWarnings("SSBasedInspection")
  @Override
  public void setVisible(boolean b) {
    super.setVisible(b);

    if (b && myRestoreFullScreen) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          toggleFullScreen(true);
          if (SystemInfo.isMacOSLion) {
            setBounds(ScreenUtil.getScreenRectangle(getLocationOnScreen()));
          }
          myRestoreFullScreen = false;
        }
      });
    }
  }

  private void installDefaultProjectStatusBarWidgets(@NotNull final Project project) {
    final StatusBar statusBar = getStatusBar();

    final PositionPanel positionPanel = new PositionPanel(project);
    statusBar.addWidget(positionPanel, "before " + IdeMessagePanel.FATAL_ERROR);

    final IdeNotificationArea notificationArea = new IdeNotificationArea();
    statusBar.addWidget(notificationArea, "before " + IdeMessagePanel.FATAL_ERROR);

    final EncodingPanel encodingPanel = new EncodingPanel(project);
    statusBar.addWidget(encodingPanel, "after Position");

    final LineSeparatorPanel lineSeparatorPanel = new LineSeparatorPanel(project);
    statusBar.addWidget(lineSeparatorPanel, "before " + encodingPanel.ID());

    final ToggleReadOnlyAttributePanel readOnlyAttributePanel = new ToggleReadOnlyAttributePanel();

    InsertOverwritePanel insertOverwritePanel = null;
    if (!SystemInfo.isMac) {
      insertOverwritePanel = new InsertOverwritePanel(project);
      statusBar.addWidget(insertOverwritePanel, "after Encoding");
      statusBar.addWidget(readOnlyAttributePanel, "after InsertOverwrite");
    } else {
      statusBar.addWidget(readOnlyAttributePanel, "after Encoding");
    }

    final InsertOverwritePanel finalInsertOverwritePanel = insertOverwritePanel;
    Disposer.register(project, new Disposable() {
      public void dispose() {
        statusBar.removeWidget(encodingPanel.ID());
        statusBar.removeWidget(lineSeparatorPanel.ID());
        statusBar.removeWidget(positionPanel.ID());
        statusBar.removeWidget(notificationArea.ID());
        statusBar.removeWidget(readOnlyAttributePanel.ID());
        if (finalInsertOverwritePanel != null) statusBar.removeWidget(finalInsertOverwritePanel.ID());

        ((StatusBarEx)statusBar).removeCustomIndicationComponents();
      }
    });
  }

  public Project getProject() {
    return myProject;
  }

  public void dispose() {
    if (isTemporaryDisposed()) {
      super.dispose();
      return;
    }
    MouseGestureManager.getInstance().remove(this);
    WelcomeFrame.notifyFrameClosed(this);

    if (myRootPane != null) {
      myRootPane = null;
    }

    if (myFrameDecorator != null) {
      Disposer.dispose(myFrameDecorator);
      myFrameDecorator = null;
    }

    FocusTrackback.release(this);

    super.dispose();
  }

  private boolean isTemporaryDisposed() {
    return myRootPane != null && myRootPane.getClientProperty(ScreenUtil.DISPOSE_TEMPORARY) != null;
  }

  public void storeFullScreenStateIfNeeded() {
    storeFullScreenStateIfNeeded(myFrameDecorator.isInFullScreen());
  }

  public void storeFullScreenStateIfNeeded(boolean state) {
    if (!WindowManager.getInstance().isFullScreenSupportedInCurrentOS()) return;

    if (myProject != null) {
      PropertiesComponent.getInstance(myProject).setValue(FULL_SCREEN, String.valueOf(state));
    }
  }

  public static boolean shouldRestoreFullScreen(@Nullable Project project) {
    return WindowManager.getInstance().isFullScreenSupportedInCurrentOS() &&
           project != null &&
           (SHOULD_OPEN_IN_FULL_SCREEN.get(project) == Boolean.TRUE || PropertiesComponent.getInstance(project).getBoolean(FULL_SCREEN, false));
  }

  @Override
  public void paint(Graphics g) {
    UIUtil.applyRenderingHints(g);
    //noinspection Since15
    super.paint(g);
  }

  public Rectangle suggestChildFrameBounds() {
//todo [kirillk] a dummy implementation
    final Rectangle b = getBounds();
    b.x += 100;
    b.width -= 200;
    b.y += 100;
    b.height -= 200;
    return b;
  }

  @Nullable
  public static Component findNearestModalComponent(@NotNull Component c) {
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent instanceof IdeFrame) return eachParent;
      if (eachParent instanceof JDialog) {
        if (((JDialog)eachParent).isModal()) return eachParent;
      }
      eachParent = eachParent.getParent();
    }

    return null;
  }

  public final BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }

  @Override
  public boolean isInFullScreen() {
    return myFrameDecorator != null && myFrameDecorator.isInFullScreen();
  }

  @Override
  public void toggleFullScreen(boolean state) {
    if (myFrameDecorator != null) {
      myFrameDecorator.toggleFullScreen(state);
    }
    IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();
    for (IdeFrame frame : frames) {
      ((IdeFrameImpl)frame).updateBorder();
    }
  }

  @Override
  public void toFront() {
    super.toFront();
  }

  @Override
  public void toBack() {
    super.toBack();
  }
}
