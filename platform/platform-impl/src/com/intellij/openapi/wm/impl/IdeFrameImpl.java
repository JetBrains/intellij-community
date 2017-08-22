/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.icons.AllIcons;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.impl.IdeNotificationArea;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.impl.ShadowPainter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.PowerSupplyKit;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class IdeFrameImpl extends JFrame implements IdeFrameEx, AccessibleContextAccessor, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.IdeFrameImpl");

  public static final Key<Boolean> SHOULD_OPEN_IN_FULL_SCREEN = Key.create("should.open.in.full.screen");

  private static boolean myUpdatingTitle;

  private String myTitle;
  private String myFileTitle;
  private File myCurrentFile;

  private Project myProject;

  private IdeRootPane myRootPane;
  private BalloonLayout myBalloonLayout;
  private IdeFrameDecorator myFrameDecorator;
  private boolean myRestoreFullScreen;

  public IdeFrameImpl(ApplicationInfoEx applicationInfoEx,
                      ActionManagerEx actionManager,
                      DataManager dataManager,
                      Application application) {
    super(applicationInfoEx.getFullApplicationName());
    myRootPane = createRootPane(actionManager, dataManager, application);
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
    MnemonicHelper.init(this);

    myBalloonLayout = new BalloonLayoutImpl(myRootPane, new Insets(8, 8, 8, 8));

    // to show window thumbnail under Macs
    // http://lists.apple.com/archives/java-dev/2009/Dec/msg00240.html
    if (SystemInfo.isMac) setIconImage(null);

    MouseGestureManager.getInstance().add(this);

    myFrameDecorator = IdeFrameDecorator.decorate(this);

    IdeMenuBar.installAppMenuIfNeeded(this);

    // UIUtil.suppressFocusStealing();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    PowerSupplyKit.checkPowerSupply();
  }

  protected IdeRootPane createRootPane(ActionManagerEx actionManager,
                                       DataManager dataManager,
                                       Application application) {
    return new IdeRootPane(actionManager, dataManager, application, this);
  }

  @NotNull
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

  @Override
  public void show() {
    super.show();
    SwingUtilities.invokeLater(() -> setFocusableWindowState(true));
  }

  /**
   * This is overridden to get rid of strange Alloy LaF customization of frames. For unknown reason it sets the maxBounds rectangle
   * and it does it plain wrong. Setting bounds to {@code null} means default value should be taken from the underlying OS.
   */
  @Override
  public synchronized void setMaximizedBounds(Rectangle bounds) {
    super.setMaximizedBounds(null);
  }

  private void setupCloseAction() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
      new WindowAdapter() {
        @Override
        public void windowClosing(@NotNull final WindowEvent e) {
          if (isTemporaryDisposed())
            return;

          final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
          if (openProjects.length > 1 || openProjects.length == 1 && SystemInfo.isMacSystemMenu) {
            if (myProject != null && myProject.isOpen()) {
              ProjectUtil.closeAndDispose(myProject);
            }
            ApplicationManager.getApplication().getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).projectFrameClosed();
            WelcomeFrame.showIfNoProjectOpened();
          }
          else {
            ApplicationManagerEx.getApplicationEx().exit();
          }
        }
      }
    );
  }

  @Override
  public StatusBar getStatusBar() {
    return myRootPane == null ? null : myRootPane.getStatusBar();
  }

  @Override
  public void setTitle(final String title) {
    if (myUpdatingTitle) {
      super.setTitle(title);
    } else {
      myTitle = title;
    }

    updateTitle();
  }

  @Override
  public void setFrameTitle(final String text) {
    super.setTitle(text);
  }

  public void setFileTitle(final String fileTitle) {
    setFileTitle(fileTitle, null);
  }

  @Override
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

      Builder builder = new Builder().append(title).append(fileTitle);
      if (!SystemInfo.isMac || builder.isEmpty()) {
        builder = builder.append(((ApplicationInfoEx)ApplicationInfo.getInstance()).getFullApplicationName());
      }
      frame.setTitle(builder.toString());
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

  @Override
  public AccessibleContext getCurrentAccessibleContext() {
    return accessibleContext;
  }

  private static final class Builder {
    private final StringBuilder sb = new StringBuilder();

    public Builder append(@Nullable String s) {
      if (!StringUtil.isEmptyOrSpaces(s)) {
        if (sb.length() > 0) sb.append(" - ");
        sb.append(s);
      }
      return this;
    }

    public boolean isEmpty() {
      return sb.length() == 0;
    }

    @Override
    public String toString() {
      return sb.toString();
    }
  }

  @Override
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

  public void setProject(@Nullable Project project) {
    if (myProject == project) {
      return;
    }

    myProject = project;
    if (project != null) {
      if (WindowManager.getInstance().isFullScreenSupportedInCurrentOS()) {
        myRestoreFullScreen = SHOULD_OPEN_IN_FULL_SCREEN.get(project) == Boolean.TRUE ||
                              ProjectFrameBounds.getInstance(project).isInFullScreen();

        if (!myRestoreFullScreen && PropertiesComponent.getInstance(project).getBoolean("FullScreen")) {
          myRestoreFullScreen = true;
          PropertiesComponent.getInstance(project).unsetValue("FullScreen");
        }
      }

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

      FocusTrackback.release(this);
    }

    if (myRestoreFullScreen && isVisible()) {
      toggleFullScreen(true);
      myRestoreFullScreen = false;
    }
  }

  @SuppressWarnings("SSBasedInspection")
  @Override
  public void setVisible(boolean b) {
    super.setVisible(b);

    if (b && myRestoreFullScreen) {
      SwingUtilities.invokeLater(() -> {
        toggleFullScreen(true);
        if (SystemInfo.isMacOSLion) {
          setBounds(ScreenUtil.getScreenRectangle(getLocationOnScreen()));
        }
        myRestoreFullScreen = false;
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

    final ToggleReadOnlyAttributePanel readOnlyAttributePanel = new ToggleReadOnlyAttributePanel(project);

    final InsertOverwritePanel insertOverwritePanel = new InsertOverwritePanel(project);
    statusBar.addWidget(insertOverwritePanel, "after Encoding");
    statusBar.addWidget(readOnlyAttributePanel, "after InsertOverwrite");

    Disposer.register(project, () -> {
      statusBar.removeWidget(encodingPanel.ID());
      statusBar.removeWidget(lineSeparatorPanel.ID());
      statusBar.removeWidget(positionPanel.ID());
      statusBar.removeWidget(notificationArea.ID());
      statusBar.removeWidget(readOnlyAttributePanel.ID());
      statusBar.removeWidget(insertOverwritePanel.ID());

      ((StatusBarEx)statusBar).removeCustomIndicationComponents();
    });
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void dispose() {
    if (SystemInfo.isMac && isInFullScreen()) {
      ((MacMainFrameDecorator)myFrameDecorator).toggleFullScreenNow();
    }
    if (isTemporaryDisposed()) {
      super.dispose();
      return;
    }
    MouseGestureManager.getInstance().remove(this);

    if (myBalloonLayout != null) {
      ((BalloonLayoutImpl)myBalloonLayout).dispose();
      myBalloonLayout = null;
    }

    // clear both our and swing hard refs
    if (myRootPane != null) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        myRootPane.removeNotify();
      }
      myRootPane = null;
      setRootPane(new JRootPane());
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
    if (myProject != null) {
      doLayout();
    }
  }

  private static final ShadowPainter ourShadowPainter = new ShadowPainter(AllIcons.Windows.Shadow.Top,
                                                                          AllIcons.Windows.Shadow.TopRight,
                                                                          AllIcons.Windows.Shadow.Right,
                                                                          AllIcons.Windows.Shadow.BottomRight,
                                                                          AllIcons.Windows.Shadow.Bottom,
                                                                          AllIcons.Windows.Shadow.BottomLeft,
                                                                          AllIcons.Windows.Shadow.Left,
                                                                          AllIcons.Windows.Shadow.TopLeft);

  @Override
  public void paint(@NotNull Graphics g) {
    UISettings.setupAntialiasing(g);
    //noinspection Since15
    super.paint(g);
    if (IdeRootPane.isFrameDecorated() && !isInFullScreen()) {
      final BufferedImage shadow = ourShadowPainter.createShadow(getRootPane(), getWidth(), getHeight());
      g.drawImage(shadow, 0, 0, null);
    }
  }

  @Override
  public Color getBackground() {
    return IdeRootPane.isFrameDecorated() ? Gray.x00.withAlpha(0) : super.getBackground();
  }

  @Override
  public void doLayout() {
    super.doLayout();
    if (!isInFullScreen() && IdeRootPane.isFrameDecorated()) {
      final int leftSide = AllIcons.Windows.Shadow.Left.getIconWidth();
      final int rightSide = AllIcons.Windows.Shadow.Right.getIconWidth();
      final int top = AllIcons.Windows.Shadow.Top.getIconHeight();
      final int bottom = AllIcons.Windows.Shadow.Bottom.getIconHeight();
      getRootPane().setBounds(leftSide, top, getWidth() - leftSide - rightSide, getHeight() - top - bottom);
    }
  }

  @Override
  public Rectangle suggestChildFrameBounds() {
    //todo [kirillk] a dummy implementation
    final Rectangle b = getBounds();
    b.x += 100;
    b.width -= 200;
    b.y += 100;
    b.height -= 200;
    return b;
  }

  @Override
  public final BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }

  @Override
  public boolean isInFullScreen() {
    return myFrameDecorator != null && myFrameDecorator.isInFullScreen();
  }

  @NotNull
  @Override
  public ActionCallback toggleFullScreen(boolean state) {

    if (temporaryFixForIdea156004(state)) return ActionCallback.DONE;

    if (myFrameDecorator != null) {
      return myFrameDecorator.toggleFullScreen(state);
    }

    return ActionCallback.DONE;
  }

  private boolean temporaryFixForIdea156004(final boolean state) {
    if (SystemInfo.isMac) {
      try {
        Field modalBlockerField = Window.class.getDeclaredField("modalBlocker");
        modalBlockerField.setAccessible(true);
        final Window modalBlocker = (Window)modalBlockerField.get(this);
        if (modalBlocker != null) {
          ApplicationManager.getApplication().invokeLater(() -> toggleFullScreen(state), ModalityState.NON_MODAL);
          return true;
        }
      }
      catch (NoSuchFieldException | IllegalAccessException e) {
        LOG.error(e);
      }
    }
    return false;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleIdeFrameImpl();
    }
    return accessibleContext;
  }

  protected class AccessibleIdeFrameImpl extends AccessibleJFrame {
    @Override
    public String getAccessibleName() {
      final StringBuilder builder = new StringBuilder();

      if (myProject != null) {
        builder.append(myProject.getName());
        builder.append(" - ");
      }

      final String applicationName = ((ApplicationInfoEx)ApplicationInfo.getInstance()).getFullApplicationName();
      builder.append(applicationName);

      return builder.toString();
    }
  }
}
