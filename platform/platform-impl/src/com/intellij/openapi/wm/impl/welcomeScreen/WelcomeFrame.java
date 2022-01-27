// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.impl.ProjectUtilCore;
import com.intellij.ide.ui.LafManager;
import com.intellij.idea.SplashManager;
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEventsKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

public final class WelcomeFrame extends JFrame implements IdeFrame, AccessibleContextAccessor {
  public static final ExtensionPointName<WelcomeFrameProvider> EP = new ExtensionPointName<>("com.intellij.welcomeFrameProvider");
  static final String DIMENSION_KEY = "WELCOME_SCREEN";

  private static IdeFrame ourInstance;
  private static Disposable ourTouchbar;

  private final WelcomeScreen myScreen;
  private final BalloonLayout myBalloonLayout;

  public WelcomeFrame() {
    SplashManager.hideBeforeShow(this);

    JRootPane rootPane = getRootPane();
    WelcomeScreen screen = createScreen(rootPane);

    IdeGlassPaneImpl glassPane = new IdeGlassPaneImpl(rootPane);
    setGlassPane(glassPane);
    glassPane.setVisible(false);
    setContentPane(screen.getWelcomePanel());
    setTitle(ApplicationNamesInfo.getInstance().getFullProductName());
    AppUIUtil.updateWindowIcon(this);

    Disposable listenerDisposable = Disposer.newDisposable();
    ApplicationManager.getApplication().getMessageBus().connect(listenerDisposable).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        Disposer.dispose(listenerDisposable);
        dispose();
      }
    });

    myBalloonLayout = new BalloonLayoutImpl(rootPane, JBUI.insets(8));

    myScreen = screen;
    setupCloseAction(this);
    MnemonicHelper.init(this);
    myScreen.setupFrame(this);

    Disposer.register(ApplicationManager.getApplication(), () -> this.dispose());
  }

  public static IdeFrame getInstance() {
    return ourInstance;
  }

  @Override
  public void dispose() {
    saveLocation(getBounds());

    super.dispose();

    Disposer.dispose(myScreen);

    resetInstance();
  }

  private static void saveLocation(Rectangle location) {
    Point middle = new Point(location.x + location.width / 2, location.y = location.height / 2);
    DimensionService.getInstance().setLocation(DIMENSION_KEY, middle, null);
  }

  static void setupCloseAction(@NotNull JFrame frame) {
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        if (ProjectUtilCore.getOpenProjects().length == 0) {
          ApplicationManager.getApplication().exit();
        }
        else {
          frame.dispose();
        }
      }
    });
  }

  private static @NotNull WelcomeScreen createScreen(JRootPane rootPane) {
    for (WelcomeScreenProvider provider : WelcomeScreenProvider.EP_NAME.getExtensionList()) {
      if (!provider.isAvailable()) {
        continue;
      }

      WelcomeScreen screen = provider.createWelcomeScreen(rootPane);
      if (screen != null) {
        return screen;
      }
    }
    return new NewWelcomeScreen();
  }

  public static void resetInstance() {
    ourInstance = null;
    if (ourTouchbar != null) {
      Disposer.dispose(ourTouchbar);
      ourTouchbar = null;
    }
  }

  public static void showNow() {
    Runnable show = prepareToShow();
    if (show != null) {
      show.run();
    }
  }

  public static @Nullable Runnable prepareToShow() {
    if (ourInstance != null) {
      return null;
    }

    // ActionManager is used on Welcome Frame, but should be initialized in a pooled thread and not in EDT.
    ForkJoinPool.commonPool().execute(() -> {
      ActionManager.getInstance();
      if (SystemInfoRt.isMac) {
        TouchbarSupport.initialize();
      }
    });

    return () -> {
      if (ourInstance != null) {
        return;
      }

      IdeFrame frame = EP.computeSafeIfAny(WelcomeFrameProvider::createFrame);
      if (frame == null) {
        throw new IllegalStateException("No implementation of `com.intellij.welcomeFrameProvider` extension point");
      }

      JFrame jFrame = (JFrame)frame;

      registerKeyboardShortcuts(jFrame.getRootPane());

      jFrame.setVisible(true);

      IdeMenuBar.installAppMenuIfNeeded(jFrame);
      ourInstance = frame;
      if (SystemInfoRt.isMac) {
        ourTouchbar = TouchbarSupport.showWindowActions(frame.getComponent());
      }
    };
  }

  private static void registerKeyboardShortcuts(@NotNull JRootPane rootPane) {
    ActionListener helpAction = e -> {
      FeatureUsageUiEventsKt.getUiEventLogger().logClickOnHelpDialog(WelcomeFrame.class.getName(), WelcomeFrame.class);
      HelpManager.getInstance().invokeHelp("welcome");
    };

    ActionUtil.registerForEveryKeyboardShortcut(rootPane, helpAction, CommonShortcuts.getContextHelp());
    rootPane.registerKeyboardAction(helpAction, KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  public static void showIfNoProjectOpened() {
    showIfNoProjectOpened(null);
  }

  public static void showIfNoProjectOpened(@Nullable AppLifecycleListener lifecyclePublisher) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    Runnable show = prepareToShow();
    if (show == null) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
      windowManager.disposeRootFrame();
      if (windowManager.getProjectFrameHelpers().isEmpty()) {
        show.run();
        if (lifecyclePublisher != null) {
          lifecyclePublisher.welcomeScreenDisplayed();
        }
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public @Nullable StatusBar getStatusBar() {
    Container pane = getContentPane();
    return pane instanceof JComponent ? UIUtil.findComponentOfType((JComponent)pane, IdeStatusBarImpl.class) : null;
  }

  @Override
  public @Nullable BalloonLayout getBalloonLayout() {
    return myBalloonLayout;
  }

  @Override
  public @NotNull Rectangle suggestChildFrameBounds() {
    return getBounds();
  }

  @Override
  public @NotNull Project getProject() {
    return ProjectManager.getInstance().getDefaultProject();
  }

  @Override
  public void setFrameTitle(String title) {
    setTitle(title);
  }

  @Override
  public JComponent getComponent() {
    return getRootPane();
  }

  @Override
  public AccessibleContext getCurrentAccessibleContext() {
    return accessibleContext;
  }
}
