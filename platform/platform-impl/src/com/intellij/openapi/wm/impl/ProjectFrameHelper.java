// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.status.*;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ProjectFrameHelper implements IdeFrameEx, AccessibleContextAccessor, DataProvider {
  private static final Logger LOG = Logger.getInstance(IdeFrameImpl.class);

  private static boolean ourUpdatingTitle;

  private String myTitle;
  private String myFileTitle;
  private File myCurrentFile;

  private Project myProject;

  private IdeRootPane myRootPane;
  private BalloonLayout myBalloonLayout;

  @Nullable
  private IdeFrameDecorator myFrameDecorator;

  private volatile Image selfie;

  private IdeFrameImpl myFrame;

  public ProjectFrameHelper(@NotNull IdeFrameImpl frame, @Nullable Image selfie) {
    myFrame = frame;
    this.selfie = selfie;
    setupCloseAction();

    preInit();
  }

  @Nullable
  public static ProjectFrameHelper getFrameHelper(@Nullable Window window) {
    if (window == null) {
      return null;
    }

    IdeFrameImpl projectFrame;
    if (window instanceof IdeFrameImpl) {
      projectFrame = (IdeFrameImpl)window;
    }
    else {
      projectFrame = (IdeFrameImpl)SwingUtilities.getAncestorOfClass(IdeFrameImpl.class, window);
      if (projectFrame == null) {
        return null;
      }
    }
    IdeFrameImpl.FrameHelper frameLightHelper = projectFrame.getFrameHelper();
    return frameLightHelper == null ? null : (ProjectFrameHelper)frameLightHelper.getHelper();
  }

  private void preInit() {
    updateTitle();

    myRootPane = new IdeRootPane(myFrame, this);
    myFrame.setRootPane(myRootPane);
    // NB!: the root pane must be set before decorator,
    // which holds its own client properties in a root pane
    myFrameDecorator = IdeFrameDecorator.decorate(myFrame, myRootPane);

    myFrame.setFrameHelper(new IdeFrameImpl.FrameHelper() {
      @Nullable
      @Override
      public Object getData(@NotNull String dataId) {
        return ProjectFrameHelper.this.getData(dataId);
      }

      @Override
      public String getAccessibleName() {
        StringBuilder builder = new StringBuilder();
        if (myProject != null) {
          builder.append(myProject.getName());
          builder.append(" - ");
        }
        builder.append(ApplicationNamesInfo.getInstance().getFullProductName());
        return builder.toString();
      }

      @Override
      public void dispose() {
        ProjectFrameHelper.this.dispose();
      }

      @Override
      public void updateView() {
        ProjectFrameHelper.this.updateView();
      }

      @Nullable
      @Override
      public Project getProject() {
        return myProject;
      }

      @NotNull
      @Override
      public IdeFrame getHelper() {
        return ProjectFrameHelper.this;
      }

      @Override
      public void setTitle(String title) {
        if (ourUpdatingTitle) {
          myFrame.doSetTitle(title);
        }
        else {
          myTitle = title;
        }

        updateTitle();
      }

      @Override
      public void releaseFrame() {
        // remove ToolWindowsPane
        myRootPane.setToolWindowsPane(null);
        WindowManagerEx.getInstanceEx().releaseFrame(ProjectFrameHelper.this);
      }
    }, myFrameDecorator);

    myBalloonLayout = new BalloonLayoutImpl(myRootPane, JBUI.insets(8));
    myFrame.setBackground(UIUtil.getPanelBackground());
  }

  // purpose of delayed init - to show project frame as earlier as possible (and start loading of project too) and use it as project loading "splash"
  // show frame -> start project loading (performed in a pooled thread) -> do UI tasks while project loading
  public void init() {
    myRootPane.init(this);

    MnemonicHelper.init(myFrame);

    myFrame.setFocusTraversalPolicy(new IdeFocusTraversalPolicy());

    // to show window thumbnail under Macs
    // http://lists.apple.com/archives/java-dev/2009/Dec/msg00240.html
    if (SystemInfo.isMac) {
      myFrame.setIconImage(null);
    }

    IdeMenuBar.installAppMenuIfNeeded(myFrame);
    // in production (not from sources) makes sense only on Linux
    AppUIUtil.updateWindowIcon(myFrame);

    MouseGestureManager.getInstance().add(this);
  }

  @Nullable
  private Component findNextFocusComponent() {
    if (myProject == null) {
      return null;
    }

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
  public JComponent getComponent() {
    return myFrame.getRootPane();
  }

  private void setupCloseAction() {
    myFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    CloseProjectWindowHelper helper = new CloseProjectWindowHelper();
    myFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(@NotNull final WindowEvent e) {
        if (isTemporaryDisposed(myFrame) || LaterInvocator.isInModalContext()) {
          return;
        }

        Application app = ApplicationManager.getApplication();
        if (app != null && (!app.isDisposeInProgress() && !app.isDisposed())) {
          helper.windowClosing(myProject);
        }
      }
    });
  }

  @Nullable
  @Override
  public IdeStatusBarImpl getStatusBar() {
    return myRootPane == null ? null : myRootPane.getStatusBar();
  }

  /**
   * @deprecated Get frame and set title directly.
   */
  @Deprecated
  public void setTitle(@NotNull String title) {
    myFrame.setTitle(title);
  }

  @Override
  public void setFrameTitle(String text) {
    myFrame.setTitle(text);
  }

  @Override
  public void setFileTitle(@Nullable String fileTitle, @Nullable File file) {
    myFileTitle = fileTitle;
    myCurrentFile = file;
    updateTitle();
  }

  @Override
  @Nullable
  public IdeRootPaneNorthExtension getNorthExtension(String key) {
    return myRootPane.findByName(key);
  }

  private void updateTitle() {
    updateTitle(myFrame, myTitle, myFileTitle, myCurrentFile);
  }

  public static @Nullable String getSuperUserSuffix() {
    return !SuperUserStatus.isSuperUser() ? null : SystemInfo.isWindows ? "(Administrator)" : "(ROOT)";
  }

  public static void updateTitle(@NotNull JFrame frame, @Nullable String title, @Nullable String fileTitle, @Nullable File currentFile) {
    if (ourUpdatingTitle) return;

    try {
      ourUpdatingTitle = true;

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
    myRootPane.updateToolbar();
    myRootPane.updateMainMenuActions();
    myRootPane.updateNorthComponents();
  }

  @Override
  public AccessibleContext getCurrentAccessibleContext() {
    return myFrame.getAccessibleContext();
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
    if (project == null) {
      //already disposed
      if (myRootPane != null) {
        myRootPane.deinstallNorthComponents();
      }
      return;
    }

    if (myRootPane != null) {
      myRootPane.installNorthComponents(project);
      StatusBar statusBar = myRootPane.getStatusBar();
      if (statusBar != null) {
        project.getMessageBus().connect().subscribe(StatusBar.Info.TOPIC, statusBar);
      }
    }

    installDefaultProjectStatusBarWidgets(myProject);
    //noinspection CodeBlock2Expr
    StartupManager.getInstance(myProject).registerPostStartupActivity((DumbAwareRunnable)() -> {
      selfie = null;
    });
  }

  private final Set<String> widgetIds = new THashSet<>();

  private boolean addWidget(@NotNull Disposable disposable, @NotNull IdeStatusBarImpl statusBar, @NotNull StatusBarWidget widget, @NotNull String anchor) {
    if (!widgetIds.add(widget.ID())) {
      LOG.error("Attempting to add more than one widget with ID: " + widget.ID());
      return false;
    }

    statusBar.doAddWidget(widget, anchor);

    final String id = widget.ID();
    Disposer.register(disposable, () -> {
      widgetIds.remove(id);
      statusBar.removeWidget(id);
    });
    return true;
  }

  private void installDefaultProjectStatusBarWidgets(@NotNull Project project) {
    IdeStatusBarImpl statusBar = Objects.requireNonNull(getStatusBar());
    addWidget(project, statusBar, new PositionPanel(project), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR));
    addWidget(project, statusBar, new IdeNotificationArea(), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR));

    LineSeparatorPanel lineSeparatorPanel = new LineSeparatorPanel(project);
    addWidget(project, statusBar, lineSeparatorPanel, StatusBar.Anchors.after(StatusBar.StandardWidgets.POSITION_PANEL));
    EncodingPanel encodingPanel = new EncodingPanel(project);
    addWidget(project, statusBar, encodingPanel, StatusBar.Anchors.after(lineSeparatorPanel.ID()));

    addWidget(project, statusBar, new ColumnSelectionModePanel(project), StatusBar.Anchors.after(encodingPanel.ID()));
    addWidget(project, statusBar, new ToggleReadOnlyAttributePanel(), StatusBar.Anchors.after(StatusBar.StandardWidgets.COLUMN_SELECTION_MODE_PANEL));

    final Map<StatusBarWidgetProvider, Disposable> providerToWidgetDisposable = new HashMap<>();
    StatusBarWidgetProvider.EP_NAME.getPoint(null).addExtensionPointListener(new ExtensionPointListener<StatusBarWidgetProvider>() {
      @Override
      public void extensionAdded(@NotNull StatusBarWidgetProvider widgetProvider, @NotNull PluginDescriptor pluginDescriptor) {
        StatusBarWidget widget = widgetProvider.getWidget(project);
        if (widget == null) {
          return;
        }

        Disposable widgetDisposable = new Disposable() {
          @Override
          public void dispose() {
            providerToWidgetDisposable.remove(widgetProvider);
          }
        };

        if (addWidget(widgetDisposable, statusBar, widget, widgetProvider.getAnchor())) {
          Disposer.register(project, widgetDisposable);
          providerToWidgetDisposable.put(widgetProvider, widgetDisposable);
        }
        statusBar.repaint();
      }

      @Override
      public void extensionRemoved(@NotNull StatusBarWidgetProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
        assert providerToWidgetDisposable.containsKey(provider);
        Disposer.dispose(providerToWidgetDisposable.get(provider));
        statusBar.repaint();
      }
    }, true, project);
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  private void dispose() {

    if (isTemporaryDisposed(myFrame)) {
      myFrame.doDispose();
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
      myFrame.setRootPane(new JRootPane());
      Disposer.dispose(myRootPane);
      myRootPane = null;
    }

    myFrame.doDispose();
    myFrameDecorator = null;
    myFrame.setFrameHelper(null, null);
    myFrame = null;
  }

  private static boolean isTemporaryDisposed(@Nullable JFrame frame) {
    JRootPane rootPane = frame == null ? null : frame.getRootPane();
    return rootPane != null && rootPane.getClientProperty(ScreenUtil.DISPOSE_TEMPORARY) != null;
  }

  @NotNull
  public IdeFrameImpl getFrame() {
    return myFrame;
  }

  @NotNull
  @ApiStatus.Internal
  IdeRootPane getRootPane() {
    return myRootPane;
  }

  @NotNull
  @Override
  public Rectangle suggestChildFrameBounds() {
    Rectangle b = myFrame.getBounds();
    b.x += 100;
    b.width -= 200;
    b.y += 100;
    b.height -= 200;
    return b;
  }

  @Nullable
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
  public Promise<?> toggleFullScreen(boolean state) {
    if (temporaryFixForIdea156004(state) || myFrameDecorator == null) {
      return Promises.resolvedPromise();
    }
    return myFrameDecorator.toggleFullScreen(state);
  }

  private boolean temporaryFixForIdea156004(final boolean state) {
    if (!SystemInfo.isMac) {
      return false;
    }

    try {
      Field modalBlockerField = Window.class.getDeclaredField("modalBlocker");
      modalBlockerField.setAccessible(true);
      Window modalBlocker = (Window)modalBlockerField.get(myFrame);
      if (modalBlocker != null) {
        ApplicationManager.getApplication().invokeLater(() -> toggleFullScreen(state), ModalityState.NON_MODAL);
        return true;
      }
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      LOG.error(e);
    }
    return false;
  }
}