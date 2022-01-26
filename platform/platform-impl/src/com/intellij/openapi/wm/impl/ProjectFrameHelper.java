// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.notification.ActionCenter;
import com.intellij.notification.NotificationsManager;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsActionGroup;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.*;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class ProjectFrameHelper implements IdeFrameEx, AccessibleContextAccessor, DataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(IdeFrameImpl.class);

  private boolean isUpdatingTitle;

  private String myTitle;
  private String fileTitle;
  private Path currentFile;

  private Project project;

  private IdeRootPane myRootPane;
  private BalloonLayout myBalloonLayout;

  private @Nullable IdeFrameDecorator myFrameDecorator;

  @SuppressWarnings("unused")
  private volatile Image selfie;

  private IdeFrameImpl frame;

  public ProjectFrameHelper(@NotNull IdeFrameImpl frame, @Nullable Image selfie) {
    this.frame = frame;
    this.selfie = selfie;
    setupCloseAction();

    preInit();

    Disposer.register(ApplicationManager.getApplication(), this);
  }

  public static @Nullable ProjectFrameHelper getFrameHelper(@Nullable Window window) {
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
    myRootPane = createIdeRootPane();
    frame.setRootPane(myRootPane);
    // NB!: the root pane must be set before decorator,
    // which holds its own client properties in a root pane
    myFrameDecorator = IdeFrameDecorator.decorate(frame, this);

    frame.setFrameHelper(new IdeFrameImpl.FrameHelper() {
      @Override
      public @Nullable Object getData(@NotNull String dataId) {
        return ProjectFrameHelper.this.getData(dataId);
      }

      @Override
      @NlsSafe
      public String getAccessibleName() {
        StringBuilder builder = new StringBuilder();
        if (project != null) {
          builder.append(project.getName());
          builder.append(" - ");
        }
        builder.append(ApplicationNamesInfo.getInstance().getFullProductName());
        return builder.toString();
      }

      @Override
      public void dispose() {
        if (isTemporaryDisposed(frame)) {
          frame.doDispose();
          return;
        }

        Disposer.dispose(ProjectFrameHelper.this);
      }

      @Override
      public void updateView() {
        ProjectFrameHelper.this.updateView();
      }

      @Override
      public @Nullable Project getProject() {
        return project;
      }

      @Override
      public @NotNull IdeFrame getHelper() {
        return ProjectFrameHelper.this;
      }

      @Override
      public void setTitle(@Nullable String title) {
        if (isUpdatingTitle) {
          frame.doSetTitle(title);
        }
        else {
          myTitle = title;
        }

        updateTitle();
      }
    }, myFrameDecorator);

    myBalloonLayout = ActionCenter.isEnabled()
                      ? new ActionCenterBalloonLayout(myRootPane, JBUI.insets(8))
                      : new BalloonLayoutImpl(myRootPane, JBUI.insets(8));
    frame.setBackground(UIUtil.getPanelBackground());
  }

  protected @NotNull IdeRootPane createIdeRootPane() {
    return new IdeRootPane(frame, this, this);
  }

  public void releaseFrame() {
    myRootPane.removeToolbar();
    WindowManagerEx.getInstanceEx().releaseFrame(this);
  }

  // purpose of delayed init - to show project frame as earlier as possible (and start loading of project too) and use it as project loading "splash"
  // show frame -> start project loading (performed in a pooled thread) -> do UI tasks while project loading
  public void init() {
    myRootPane.createAndConfigureStatusBar(this, this);
    MnemonicHelper.init(frame);
    frame.setFocusTraversalPolicy(new IdeFocusTraversalPolicy());

    // to show window thumbnail under Macs
    // http://lists.apple.com/archives/java-dev/2009/Dec/msg00240.html
    if (SystemInfoRt.isMac) {
      frame.setIconImage(null);
    }

    IdeMenuBar.installAppMenuIfNeeded(frame);
    // in production (not from sources) makes sense only on Linux
    AppUIUtil.updateWindowIcon(frame);

    MouseGestureManager.getInstance().add(this);

    ApplicationManager.getApplication().invokeLater(
      () -> ((NotificationsManagerImpl)NotificationsManager.getNotificationsManager()).dispatchEarlyNotifications(),
      ModalityState.NON_MODAL,
      ignored -> frame == null);
  }

  @Override
  public JComponent getComponent() {
    return frame.getRootPane();
  }

  private void setupCloseAction() {
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    CloseProjectWindowHelper helper = createCloseProjectWindowHelper();
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(@NotNull WindowEvent e) {
        if (isTemporaryDisposed(frame) || LaterInvocator.isInModalContext()) {
          return;
        }

        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isDisposed()) {
          helper.windowClosing(project);
        }
      }
    });
  }

  protected @NotNull CloseProjectWindowHelper createCloseProjectWindowHelper() {
    return new CloseProjectWindowHelper();
  }

  @Override
  public @Nullable IdeStatusBarImpl getStatusBar() {
    return myRootPane == null ? null : myRootPane.getStatusBar();
  }

  @Override
  public void setFrameTitle(String text) {
    frame.setTitle(text);
  }

  void frameReleased() {
    if (project != null) {
      project = null;
      // already disposed
      if (myRootPane != null) {
        myRootPane.deinstallNorthComponents();
      }
    }

    fileTitle = null;
    currentFile = null;
    myTitle = null;
    if (frame != null) {
      frame.doSetTitle("");
    }
  }

  @Override
  public void setFileTitle(@Nullable String fileTitle, @Nullable Path file) {
    this.fileTitle = fileTitle;
    currentFile = file;
    updateTitle();
  }

  @Override
  public @Nullable IdeRootPaneNorthExtension getNorthExtension(String key) {
    return myRootPane.findByName(key);
  }

  protected @NotNull List<TitleInfoProvider> getTitleInfoProviders() {
    return TitleInfoProvider.EP.getExtensionList();
  }

  void updateTitle() {
    if (isUpdatingTitle) {
      return;
    }

    isUpdatingTitle = true;
    try {
      if (AdvancedSettings.getBoolean("ide.show.fileType.icon.in.titleBar")) {
        File ioFile = currentFile == null ? null : currentFile.toFile();
        frame.getRootPane().putClientProperty("Window.documentFile", ioFile); // this property requires java.io.File
      }

      StringBuilder builder = new StringBuilder();
      appendTitlePart(builder, myTitle);
      appendTitlePart(builder, fileTitle);
      List<TitleInfoProvider> titleInfoProviders = getTitleInfoProviders();
      if (!titleInfoProviders.isEmpty()) {
        assert project != null;
        for (TitleInfoProvider extension : titleInfoProviders) {
          if (extension.isActive(project)) {
            String it = extension.getValue(project);
            if (!it.isEmpty()) {
              appendTitlePart(builder, it, " ");
            }
          }
        }
      }

      if (builder.length() > 0) {
        frame.doSetTitle(builder.toString());
      }
    }
    finally {
      isUpdatingTitle = false;
    }
  }

  public static @Nullable String getSuperUserSuffix() {
    return !SuperUserStatus.isSuperUser() ? null : SystemInfoRt.isWindows ? "Administrator" : "ROOT";
  }

  public void updateView() {
    myRootPane.updateToolbar();
    myRootPane.updateMainMenuActions();
    myRootPane.updateNorthComponents();
  }

  @Override
  public AccessibleContext getCurrentAccessibleContext() {
    return frame.getAccessibleContext();
  }

  public static void appendTitlePart(@NotNull StringBuilder sb, @Nullable String s) {
    appendTitlePart(sb, s, " \u2013 ");
  }

  private static void appendTitlePart(@NotNull StringBuilder sb, @Nullable String s, String separator) {
    if (!Strings.isEmptyOrSpaces(s)) {
      if (sb.length() > 0) {
        sb.append(separator);
      }
      sb.append(s);
    }
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return project != null && project.isInitialized() ? project : null;
    }
    else if (IdeFrame.KEY.is(dataId)) {
      return this;
    }
    else if (PlatformDataKeys.LAST_ACTIVE_TOOL_WINDOWS.is(dataId)) {
      ToolWindowManager manager = project != null && project.isInitialized() ? project.getServiceIfCreated(ToolWindowManager.class) : null;
      return manager instanceof ToolWindowManagerImpl ? JBIterable.from(
        ((ToolWindowManagerImpl)manager).getLastActiveToolWindows()).toArray(new ToolWindow[0]) : null;
    }

    return null;
  }

  @Override
  public @Nullable Project getProject() {
    return project;
  }

  public void setProject(@NotNull Project project) {
    if (this.project == project) {
      return;
    }

    this.project = project;
    if (myRootPane != null) {
      myRootPane.setProject(project);
      myRootPane.installNorthComponents(project);
      StatusBar statusBar = myRootPane.getStatusBar();
      if (statusBar != null) {
        project.getMessageBus().connect().subscribe(StatusBar.Info.TOPIC, statusBar);

        if (ExperimentalUI.isNewUI()) {
          var navBar = myRootPane.findByName(IdeStatusBarImpl.NAVBAR_WIDGET_KEY);
          if (navBar instanceof StatusBarCentralWidget) {
            statusBar.setCentralWidget((StatusBarCentralWidget)navBar);
          }
        }
      }
    }

    installDefaultProjectStatusBarWidgets(project);
    updateTitle();
    if (selfie != null) {
      StartupManager.getInstance(project).runAfterOpened(() -> {
        selfie = null;
      });
    }

    if (myFrameDecorator != null) {
      myFrameDecorator.setProject();
    }
  }

  protected void installDefaultProjectStatusBarWidgets(@NotNull Project project) {
    project.getService(StatusBarWidgetsManager.class).installPendingWidgets();
    IdeStatusBarImpl statusBar = Objects.requireNonNull(getStatusBar());
    PopupHandler.installPopupMenu(statusBar, StatusBarWidgetsActionGroup.GROUP_ID, ActionPlaces.STATUS_BAR_PLACE);
  }

  @Override
  public void dispose() {
    MouseGestureManager.getInstance().remove(this);

    if (myBalloonLayout != null) {
      //noinspection SSBasedInspection
      ((BalloonLayoutImpl)myBalloonLayout).dispose();
      myBalloonLayout = null;
    }

    // clear both our and swing hard refs
    if (myRootPane != null) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        myRootPane.removeNotify();
      }
      frame.setRootPane(new JRootPane());
      myRootPane = null;
    }

    if (frame != null) {
      frame.doDispose();
      frame.setFrameHelper(null, null);
      frame = null;
    }
    myFrameDecorator = null;
  }

  private static boolean isTemporaryDisposed(@Nullable RootPaneContainer frame) {
    return UIUtil.isClientPropertyTrue(frame == null ? null : frame.getRootPane(), ScreenUtil.DISPOSE_TEMPORARY);
  }

  public @Nullable IdeFrameImpl getFrame() {
    IdeFrameImpl frame = this.frame;
    if (frame == null) {
      if (Disposer.isDisposed(this)) {
        LOG.error(getClass().getSimpleName() + " is already disposed");
      }
      else {
        LOG.error("Frame is null, but " + getClass().getSimpleName() + " is not disposed yet");
      }
    }
    return frame;
  }

  public @NotNull IdeFrameImpl requireNotNullFrame() {
    IdeFrameImpl frame = this.frame;
    if (frame != null) {
      return frame;
    }
    if (Disposer.isDisposed(this)) {
      throw new AssertionError(getClass().getSimpleName() + " is already disposed");
    }
    else {
      throw new AssertionError("Frame is null, but " + getClass().getSimpleName() + " is not disposed yet");
    }
  }

  @ApiStatus.Internal
  @Nullable IdeRootPane getRootPane() {
    return myRootPane;
  }

  @Override
  public @NotNull Rectangle suggestChildFrameBounds() {
    Rectangle b = frame.getBounds();
    b.x += 100;
    b.width -= 200;
    b.y += 100;
    b.height -= 200;
    return b;
  }

  @Override
  public final @Nullable BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }

  @Override
  public boolean isInFullScreen() {
    return myFrameDecorator != null && myFrameDecorator.isInFullScreen();
  }

  @Override
  public @NotNull Promise<?> toggleFullScreen(boolean state) {
    if (temporaryFixForIdea156004(state) || myFrameDecorator == null) {
      return Promises.resolvedPromise();
    }
    else {
      return myFrameDecorator.toggleFullScreen(state);
    }
  }

  private boolean temporaryFixForIdea156004(boolean state) {
    if (SystemInfoRt.isMac) {
      try {
        Field modalBlockerField = Window.class.getDeclaredField("modalBlocker");
        modalBlockerField.setAccessible(true);
        Window modalBlocker = (Window)modalBlockerField.get(frame);
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
}
