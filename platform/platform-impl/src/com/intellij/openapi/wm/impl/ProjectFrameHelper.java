// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsActionGroup;
import com.intellij.ui.*;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import kotlin.Unit;
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

  private static boolean ourUpdatingTitle;

  private String myTitle;
  private String myFileTitle;
  private Path myCurrentFile;

  private Project myProject;

  private IdeRootPane myRootPane;
  private BalloonLayout myBalloonLayout;

  private @Nullable IdeFrameDecorator myFrameDecorator;

  @SuppressWarnings("unused")
  private volatile Image selfie;

  private IdeFrameImpl myFrame;

  public ProjectFrameHelper(@NotNull IdeFrameImpl frame, @Nullable Image selfie) {
    myFrame = frame;
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
    updateTitle();

    myRootPane = createIdeRootPane();
    myFrame.setRootPane(myRootPane);
    // NB!: the root pane must be set before decorator,
    // which holds its own client properties in a root pane
    myFrameDecorator = IdeFrameDecorator.decorate(myFrame, this);

    myFrame.setFrameHelper(new IdeFrameImpl.FrameHelper() {
      @Override
      public @Nullable Object getData(@NotNull String dataId) {
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
        if (isTemporaryDisposed(myFrame)) {
          myFrame.doDispose();
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
        return myProject;
      }

      @Override
      public @NotNull IdeFrame getHelper() {
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
    }, myFrameDecorator);

    myBalloonLayout = new BalloonLayoutImpl(myRootPane, JBUI.insets(8));
    myFrame.setBackground(UIUtil.getPanelBackground());
  }

  protected @NotNull IdeRootPane createIdeRootPane() {
    return new IdeRootPane(myFrame, this, this);
  }

  public void releaseFrame() {
    myRootPane.removeToolbar();
    WindowManagerEx.getInstanceEx().releaseFrame(this);
  }

  // purpose of delayed init - to show project frame as earlier as possible (and start loading of project too) and use it as project loading "splash"
  // show frame -> start project loading (performed in a pooled thread) -> do UI tasks while project loading
  public void init() {
    myRootPane.init(this, this);

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

  @Override
  public JComponent getComponent() {
    return myFrame.getRootPane();
  }

  private void setupCloseAction() {
    myFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    CloseProjectWindowHelper helper = createCloseProjectWindowHelper();
    myFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(@NotNull WindowEvent e) {
        if (isTemporaryDisposed(myFrame) || LaterInvocator.isInModalContext()) {
          return;
        }

        Application app = ApplicationManager.getApplication();
        if (app != null && !app.isDisposed()) {
          helper.windowClosing(myProject);
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
  public void setFileTitle(@Nullable String fileTitle, @Nullable Path file) {
    myFileTitle = fileTitle;
    myCurrentFile = file;
    updateTitle();
  }

  @Override
  public @Nullable IdeRootPaneNorthExtension getNorthExtension(String key) {
    return myRootPane.findByName(key);
  }

  private void updateTitle() {
    updateTitle(myFrame, myTitle, myFileTitle, myCurrentFile, myTitleInfoExtensions);
  }

  public static @Nullable
  String getSuperUserSuffix() {
    return !SuperUserStatus.isSuperUser() ? null : SystemInfo.isWindows ? "Administrator" : "ROOT";
  }

  private List<TitleInfoProvider> myTitleInfoExtensions = null;

  public static void updateTitle(@NotNull JFrame frame,
                                 @Nullable String title,
                                 @Nullable String fileTitle,
                                 @Nullable Path currentFile,
                                 @Nullable List<TitleInfoProvider> extensions) {
    if (ourUpdatingTitle) {
      return;
    }

    try {
      ourUpdatingTitle = true;

      if (Registry.is("ide.show.fileType.icon.in.titleBar")) {
        File ioFile = currentFile != null ? currentFile.toFile() : null;
        frame.getRootPane().putClientProperty("Window.documentFile", ioFile); // this property requires java.io.File
      }

      Builder builder = new Builder().append(title).append(fileTitle);
      if (extensions != null && !extensions.isEmpty()) {
        for (TitleInfoProvider extension : extensions) {
          if (extension.isActive()) {
            String it = extension.getValue();
            if (!it.isEmpty()) {
              builder.append(it, " ");
            }
          }
        }
      }

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
      return append(s, " \u2013 ");
    }

    Builder append(@Nullable String s, String separator) {
      if (!StringUtil.isEmptyOrSpaces(s)) {
        if (sb.length() > 0) sb.append(separator);
        sb.append(s);
      }
      return this;
    }

    @Override
    public String toString() {
      return sb.toString();
    }
  }

  @Override
  public Object getData(@NotNull String dataId) {
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
      // already disposed
      if (myRootPane != null) {
        myRootPane.deinstallNorthComponents();
      }
      return;
    }

    if (myRootPane != null) {
      myRootPane.setProject(project);
      myRootPane.installNorthComponents(project);
      StatusBar statusBar = myRootPane.getStatusBar();
      if (statusBar != null) {
        project.getMessageBus().connect().subscribe(StatusBar.Info.TOPIC, statusBar);
      }
    }

    installDefaultProjectStatusBarWidgets(myProject);
    initTitleInfoProviders(project);
    if (selfie != null) {
      StartupManager.getInstance(myProject).registerPostStartupActivity((DumbAwareRunnable)() -> {
        selfie = null;
      });
    }
  }

  protected void initTitleInfoProviders(@NotNull Project project) {
    myTitleInfoExtensions = TitleInfoProvider.getProviders(project, (it) -> {
      updateTitle();
      return Unit.INSTANCE;
    });
  }

  protected void installDefaultProjectStatusBarWidgets(@NotNull Project project) {
    PopupHandler.installPopupHandler(Objects.requireNonNull(getStatusBar()), StatusBarWidgetsActionGroup.GROUP_ID, ActionPlaces.STATUS_BAR_PLACE);
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void dispose() {
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
      myRootPane = null;
    }

    if (myFrame != null) {
      myFrame.doDispose();
      myFrame.setFrameHelper(null, null);
      myFrame = null;
    }
    myFrameDecorator = null;
  }

  private static boolean isTemporaryDisposed(@Nullable RootPaneContainer frame) {
    return UIUtil.isClientPropertyTrue(frame == null ? null : frame.getRootPane(), ScreenUtil.DISPOSE_TEMPORARY);
  }

  public @NotNull IdeFrameImpl getFrame() {
    return myFrame;
  }

  @ApiStatus.Internal
  public @Nullable IdeFrameImpl getFrameOrNullIfDisposed() {
    return myFrame;
  }

  @ApiStatus.Internal
  @Nullable IdeRootPane getRootPane() {
    return myRootPane;
  }

  @Override
  public @NotNull Rectangle suggestChildFrameBounds() {
    Rectangle b = myFrame.getBounds();
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