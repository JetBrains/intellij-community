// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.project.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.status.*;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.PowerSupplyKit;

import javax.accessibility.AccessibleContext;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IdeFrameImpl extends JFrame implements IdeFrameEx, AccessibleContextAccessor, DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.IdeFrameImpl");

  public static final String NORMAL_STATE_BOUNDS = "normalBounds";
  public static final Key<Boolean> SHOULD_OPEN_IN_FULL_SCREEN = Key.create("should.open.in.full.screen");

  private static boolean ourUpdatingTitle;

  private String myTitle;
  private String myFileTitle;
  private File myCurrentFile;

  private Project myProject;

  private IdeRootPane myRootPane;
  private BalloonLayout myBalloonLayout;
  private IdeFrameDecorator myFrameDecorator;
  private boolean myRestoreFullScreen;
  private final LafManagerListener myLafListener;
  private final ComponentListener resizedListener;

  private boolean ready;
  private Image mySelfie;

  public IdeFrameImpl() {
    super();
    updateTitle();

    myRootPane = new IdeRootPane(this);
    setRootPane(myRootPane);
    setBackground(UIUtil.getPanelBackground());
    LafManager.getInstance().addLafManagerListener(myLafListener = src -> setBackground(UIUtil.getPanelBackground()));
    AppUIUtil.updateWindowIcon(this);

    resizedListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (getExtendedState() == Frame.NORMAL) {
          getRootPane().putClientProperty(NORMAL_STATE_BOUNDS, getBounds());
        }
      }
    };

    myRootPane.addComponentListener(resizedListener);

    Dimension size = ScreenUtil.getMainScreenBounds().getSize();
    size.width = Math.min(1400, size.width - 20);
    size.height = Math.min(1000, size.height - 40);
    setSize(size);
    setLocationRelativeTo(null);
    setMinimumSize(new Dimension(340, getMinimumSize().height));

    if (Registry.is("suppress.focus.stealing") &&
        Registry.is("suppress.focus.stealing.auto.request.focus") &&
        !ApplicationManager.getApplication().isActive()) {
      setAutoRequestFocus(false);
    }

    //LayoutFocusTraversalPolicyExt layoutFocusTraversalPolicy = new LayoutFocusTraversalPolicyExt();
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicyExt() {
      @Override
      public Component getComponentAfter(Container focusCycleRoot, Component aComponent) {
        // Every time a component is removed, AWT asks focus layout policy
        // who is supposed to be the next focus owner.
        // Looks like for IdeFrame, the selected editor of the frame is a good candidate
        if (myProject != null) {
          final FileEditorManagerEx fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(myProject);
          if (fileEditorManagerEx != null) {
            final EditorWindow window = fileEditorManagerEx.getCurrentWindow();
            if (window != null) {
              final EditorWithProviderComposite editor = window.getSelectedEditor();
              if (editor != null) {
                final JComponent component = editor.getPreferredFocusedComponent();
                if (component != null) {
                  return component;
                }
              }
            }
          }
        }
        return super.getComponentAfter(focusCycleRoot, aComponent);
      }
    });

    setupCloseAction();
    MnemonicHelper.init(this);

    myBalloonLayout = new BalloonLayoutImpl(myRootPane, JBUI.insets(8));

    // to show window thumbnail under Macs
    // http://lists.apple.com/archives/java-dev/2009/Dec/msg00240.html
    if (SystemInfo.isMac) {
      setIconImage(null);
    }

    MouseGestureManager.getInstance().add(this);

    myFrameDecorator = IdeFrameDecorator.decorate(this);

    setFocusTraversalPolicy(new LayoutFocusTraversalPolicyExt()    {
      @Override
      protected Component getDefaultComponentImpl(Container focusCycleRoot) {
        Component component = findNextFocusComponent();
        return component == null ? super.getDefaultComponentImpl(focusCycleRoot) : component;
      }

      @Override
      protected Component getFirstComponentImpl(Container focusCycleRoot) {
        Component component = findNextFocusComponent();
        return component == null ? super.getFirstComponentImpl(focusCycleRoot) : component;
      }

      @Override
      protected Component getLastComponentImpl(Container focusCycleRoot) {
        Component component = findNextFocusComponent();
        return component == null ? super.getLastComponentImpl(focusCycleRoot) : component;
      }

      @Override
      protected Component getComponentAfterImpl(Container focusCycleRoot, Component aComponent) {
        Component component = findNextFocusComponent();
        return component == null ? super.getComponentAfterImpl(focusCycleRoot, aComponent) : component;
      }

      @Override
      public Component getInitialComponent(Window window) {
        Component component = findNextFocusComponent();
        return component == null ? super.getInitialComponent(window) : component;
      }

      @Override
      protected Component getComponentBeforeImpl(Container focusCycleRoot, Component aComponent) {
        Component component = findNextFocusComponent();
        return component == null ? super.getComponentBeforeImpl(focusCycleRoot, aComponent) : component;
      }
    });
  }

  private Component findNextFocusComponent() {
    if (myProject != null) {
      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      ToolWindowManagerEx toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(myProject);
      if (ToolWindowManagerEx.getInstanceEx(myProject).fallbackToEditor()) {
        EditorWindow currentWindow = FileEditorManagerEx.getInstanceEx(myProject).getSplitters().getCurrentWindow();
        if (currentWindow != null) {
          EditorWithProviderComposite selectedEditor = currentWindow.getSelectedEditor();
          if (selectedEditor != null) {
            JComponent preferredFocusedComponent = selectedEditor.getPreferredFocusedComponent();
            if (preferredFocusedComponent != null) {
              return preferredFocusedComponent;
            }
          }
        }
      }
      else if (focusOwner != null && !Windows.ToolWindowProvider.isInToolWindow(focusOwner)) {
        String toolWindowId = toolWindowManagerEx.getLastActiveToolWindowId();
        ToolWindow toolWindow = toolWindowManagerEx.getToolWindow(toolWindowId);
        Content content = toolWindow == null ? null : toolWindow.getContentManager().getContent(0);
        if (content != null) {
          JComponent component = content.getPreferredFocusableComponent();
          if (component == null) {
            LOG.warn("Set preferredFocusableComponent in '" + content.getDisplayName() + "' content in " +
                     toolWindowId + " tool window to avoid focus-related problems.");
          }
          return component == null ? getComponentToRequestFocus(toolWindow)  : component;
        }
      }
    }
    return null;
  }

  @Nullable
  private static Component getComponentToRequestFocus(ToolWindow toolWindow) {
    Container container = toolWindow.getComponent();
    if (container == null || !container.isShowing()) {
      LOG.warn(toolWindow.getTitle() + " tool window - parent container is hidden");
      return null;
    }
    FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
    if (policy == null) {
      LOG.warn(toolWindow.getTitle() + " tool window does not provide focus traversal policy");
      return null;
    }
    Component component = policy.getDefaultComponent(container);
    if (component == null || !component.isShowing()) {
      LOG.debug(toolWindow.getTitle() + " tool window - default component is hidden");
      return null;
    }
    return component;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    PowerSupplyKit.checkPowerSupply();
  }

  @NotNull
  @Override
  public Insets getInsets() {
    return SystemInfo.isMac && isInFullScreen() ? JBUI.emptyInsets() : super.getInsets();
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
  @SuppressWarnings({"SSBasedInspection", "deprecation"})
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

  @Override
  public void setExtendedState(int state) {
    if (getExtendedState() == Frame.NORMAL && (state & Frame.MAXIMIZED_BOTH) != 0) {
      getRootPane().putClientProperty(NORMAL_STATE_BOUNDS, getBounds());
    }
    super.setExtendedState(state);
  }

  private void setupCloseAction() {
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    CloseProjectWindowHelper helper = new CloseProjectWindowHelper();
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(@NotNull final WindowEvent e) {
        if (isTemporaryDisposed() || LaterInvocator.isInModalContext()) {
          return;
        }

        Application app = ApplicationManager.getApplication();
        if (app != null && (!app.isDisposeInProgress() && !app.isDisposed())) {
          helper.windowClosing(myProject);
        }
      }
    });
  }

  @Override
  public StatusBar getStatusBar() {
    return myRootPane == null ? null : myRootPane.getStatusBar();
  }

  @Override
  public void setTitle(final String title) {
    if (ourUpdatingTitle) {
      super.setTitle(title);
    }
    else {
      myTitle = title;
    }

    updateTitle();
  }

  @Override
  public void setFrameTitle(String text) {
    super.setTitle(text);
  }

  @Override
  public void setFileTitle(@Nullable String fileTitle, @Nullable File file) {
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

  public static @Nullable String getSuperUserSuffix() {
    return !SuperUserStatus.isSuperUser() ? null : SystemInfo.isWindows ? "(Administrator)" : "(ROOT)";
  }

  public static void updateTitle(@NotNull JFrame frame, @Nullable String title, @Nullable String fileTitle, @Nullable File currentFile) {
    if (ourUpdatingTitle) return;

    try {
      ourUpdatingTitle = true;

      if (IdeFrameDecorator.isCustomDecoration()) {
        frame.getRootPane().putClientProperty("Window.CustomDecoration.documentFile", currentFile);
      }

      if (Registry.is("ide.show.fileType.icon.in.titleBar")) {
        frame.getRootPane().putClientProperty("Window.documentFile", currentFile);
      }

      Builder builder = new Builder().append(title).append(fileTitle);
      if (Boolean.getBoolean("ide.ui.version.in.title")) {
        builder.append(ApplicationNamesInfo.getInstance().getFullProductName() + ' ' + ApplicationInfo.getInstance().getFullVersion());
      }
      else if (!SystemInfo.isMac && !SystemInfo.isGNOME || builder.isEmpty()) {
        builder.append(ApplicationNamesInfo.getInstance().getFullProductName());
      }
      builder.append(getSuperUserSuffix(), " ");
      frame.setTitle(builder.toString());
    }
    finally {
      ourUpdatingTitle = false;
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

    Builder append(@Nullable String s) {
      return append(s, " - ");
    }

    Builder append(@Nullable String s, String separator) {
      if (!StringUtil.isEmptyOrSpaces(s)) {
        if (sb.length() > 0) sb.append(separator);
        sb.append(s);
      }
      return this;
    }

    boolean isEmpty() {
      return sb.length() == 0;
    }

    @Override
    public String toString() {
      return sb.toString();
    }
  }

  @Override
  public Object getData(@NotNull final String dataId) {
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
        StatusBar statusBar = myRootPane.getStatusBar();
        if (statusBar != null) {
          project.getMessageBus().connect().subscribe(StatusBar.Info.TOPIC, statusBar);
        }
      }

      installDefaultProjectStatusBarWidgets(myProject);

      ProjectManager.getInstance().addProjectManagerListener(myProject, new ProjectManagerListener() {
        @Override
        public void projectClosingBeforeSave(@NotNull Project project) {
          takeASelfie();
        }
      });

      StartupManager.getInstance(myProject).registerPostStartupActivity((DumbAwareRunnable)() -> ready = true);
    }
    else {
      if (myRootPane != null) { //already disposed
        myRootPane.deinstallNorthComponents();
      }
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

  private final Set<String> widgetIDs = new HashSet<>();

  private void addWidget(StatusBar statusBar, StatusBarWidget widget, String anchor) {
    if (!widgetIDs.add(widget.ID())) {
      LOG.error("Attempting to add more than one widget with ID: " + widget.ID());
      return;
    }
    statusBar.addWidget(widget, anchor);
  }

  private void installDefaultProjectStatusBarWidgets(@NotNull final Project project) {
    final StatusBar statusBar = getStatusBar();
    addWidget(statusBar, new PositionPanel(project), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR));
    addWidget(statusBar, new IdeNotificationArea(project), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR));
    addWidget(statusBar, new EncodingPanel(project), StatusBar.Anchors.after(StatusBar.StandardWidgets.POSITION_PANEL));
    addWidget(statusBar, new LineSeparatorPanel(project), StatusBar.Anchors.before(StatusBar.StandardWidgets.ENCODING_PANEL));
    addWidget(statusBar, new ColumnSelectionModePanel(project), StatusBar.Anchors.after(StatusBar.StandardWidgets.ENCODING_PANEL));
    addWidget(statusBar, new ToggleReadOnlyAttributePanel(project),
              StatusBar.Anchors.after(StatusBar.StandardWidgets.COLUMN_SELECTION_MODE_PANEL));

    for (StatusBarWidgetProvider widgetProvider: StatusBarWidgetProvider.EP_NAME.getExtensions()) {
      StatusBarWidget widget = widgetProvider.getWidget(project);
      if (widget == null) continue;
      addWidget(statusBar, widget, widgetProvider.getAnchor());
    }

    Disposer.register(project, () -> {
      for (String widgetID: widgetIDs) {
        statusBar.removeWidget(widgetID);
      }
      widgetIDs.clear();

      if (statusBar instanceof IdeStatusBarImpl) {
        ((IdeStatusBarImpl)statusBar).removeCustomIndicationComponents();
      }
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
      myRootPane.removeComponentListener(resizedListener);
      setRootPane(new JRootPane());
      Disposer.dispose(myRootPane);
      myRootPane = null;
    }
    if (myFrameDecorator != null) {
      Disposer.dispose(myFrameDecorator);
      myFrameDecorator = null;
    }
    if (myLafListener != null) LafManager.getInstance().removeLafManagerListener(myLafListener);

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

  @Override
  public void paint(@NotNull Graphics g) {
    UISettings.setupAntialiasing(g);
    if (shouldPaintSelfie()) {
      try {
        if (mySelfie == null) {
          mySelfie = ImageUtil.ensureHiDPI(ImageIO.read(getSelfieLocation()), ScaleContext.create(this));
        }
      } catch (IOException ignored) {}
      StartupUiUtil.drawImage(g, mySelfie, 0, 0, null);
      return;
    } else {
      mySelfie = null;
    }
    super.paint(g);
  }

  private boolean shouldPaintSelfie() {
    return !ready && Registry.is("ide.project.loading.show.last.state") &&
           (myProject != null || ProjectManager.getInstance().getOpenProjects().length == 0);
  }

  public void takeASelfie() {
    if (myProject == null || !Registry.is("ide.project.loading.show.last.state")) return;
    BufferedImage image = UIUtil.createImage(this, getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
    UISettings.setupAntialiasing(image.getGraphics());
    paint(image.getGraphics());
    try {
      File selfie = getSelfieLocation();
      if (selfie.getParentFile().exists() || selfie.getParentFile().mkdirs()) {
        ImageIO.write(image, "png", selfie);
        FileUtil.copy(selfie, getLastSelfieLocation());
      }
    } catch (IOException ignored) {}
  }

  @NotNull
  private File getSelfieLocation() {
    return myProject != null ?
           ProjectUtil.getProjectCachePath(myProject, "selfies", false, ".png").toFile() :
           getLastSelfieLocation();
  }

  @NotNull
  private static File getLastSelfieLocation() {
    return new File(PathManager.getSystemPath(), "selfies/last_closed_project.png");
  }

  @Override
  public Rectangle suggestChildFrameBounds() {
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

      builder.append(ApplicationNamesInfo.getInstance().getFullProductName());

      return builder.toString();
    }
  }
}