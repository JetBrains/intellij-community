// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ExtensionPoints;
import com.intellij.Patches;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeRepaintManager;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.ui.Splash;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static com.intellij.openapi.application.JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY;

public class IdeaApplication {
  public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";
  public static final String IDEA_IS_UNIT_TEST = "idea.is.unit.test";

  private static final String[] SAFE_JAVA_ENV_PARAMETERS = {REQUIRED_PLUGINS_KEY};

  static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  private static IdeaApplication ourInstance;

  public static IdeaApplication getInstance() {
    return ourInstance;
  }

  public static boolean isLoaded() {
    return ourInstance != null && ourInstance.myLoaded;
  }

  @SuppressWarnings("SSBasedInspection")
  public static void initApplication(String[] args) {
    IdeaApplication app = new IdeaApplication(args);
    // this invokeLater() call is needed to place the app starting code on a freshly minted IdeEventQueue instance
    SwingUtilities.invokeLater(() -> {
      PluginManager.installExceptionHandler();
      app.run();
    });
  }

  private final @NotNull String[] myArgs;
  private static boolean myPerformProjectLoad = true;
  private ApplicationStarter myStarter;
  private volatile boolean myLoaded;

  public IdeaApplication(@NotNull String[] args) {
    LOG.assertTrue(ourInstance == null);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = this;
    myArgs = processProgramArguments(args);
    boolean isInternal = Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY);
    boolean isUnitTest = Boolean.getBoolean(IDEA_IS_UNIT_TEST);
    boolean isOnAir = "onair".equals(System.getProperty(ExtensionPoints.APPLICATION_STARTER));
    boolean isShowSplash = !Boolean.getBoolean(StartupUtil.NO_SPLASH);

    boolean headless = Main.isHeadless();
    patchSystem(headless);

    myStarter = getStarter();

    if (Main.isCommandLine()) {
      if (CommandLineApplication.ourInstance == null) {
        new CommandLineApplication(isInternal, isUnitTest, headless, isOnAir);
      }
      if (isUnitTest) {
        myLoaded = true;
      }
    }
    else {
      Splash splash = null;
      myStarter = getStarter();
      if (isShowSplash && myStarter instanceof IdeStarter) {
        splash = ((IdeStarter)myStarter).showSplash();
      }
      ApplicationManagerEx.createApplication(isInternal, isUnitTest, false, false, ApplicationManagerEx.IDEA_APPLICATION, splash, isOnAir);
    }

    if (headless && myStarter instanceof ApplicationStarterEx && !((ApplicationStarterEx)myStarter).isHeadless()) {
      Main.showMessage("Startup Error", "Application cannot start in headless mode", true);
      System.exit(Main.NO_GRAPHICS);
    }

    myStarter.premain(myArgs);
  }

  /**
   * Method looks for -Dkey=value program arguments and stores some of them in system properties.
   * We should use it for a limited number of safe keys.
   * One of them is a list of ids of required plugins
   *
   * @see IdeaApplication#SAFE_JAVA_ENV_PARAMETERS
   */
  @NotNull
  private static String[] processProgramArguments(@NotNull String[] args) {
    ArrayList<String> arguments = new ArrayList<>();
    List<String> safeKeys = Arrays.asList(SAFE_JAVA_ENV_PARAMETERS);
    for (String arg : args) {
      if (arg.startsWith("-D")) {
        String[] keyValue = arg.substring(2).split("=");
        if (keyValue.length == 2 && safeKeys.contains(keyValue[0])) {
          System.setProperty(keyValue[0], keyValue[1]);
          continue;
        }
      }
      if (StartupUtil.NO_SPLASH.equals(arg)) {
        System.setProperty(StartupUtil.NO_SPLASH, "true");
        continue;
      }
      arguments.add(arg);
    }
    return ArrayUtil.toStringArray(arguments);
  }

  private static void patchSystem(boolean headless) {
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(headless);
    LOG.info("CPU cores: " + Runtime.getRuntime().availableProcessors()+"; ForkJoinPool.commonPool: " + ForkJoinPool.commonPool() + "; factory: " + ForkJoinPool.commonPool().getFactory());

    System.setProperty("sun.awt.noerasebackground", "true");

    //noinspection ResultOfMethodCallIgnored
    IdeEventQueue.getInstance();  // replaces system event queue

    if (headless) return;

    /* Using custom RepaintManager disables BufferStrategyPaintManager (and so, true double buffering)
       because the only non-private constructor forces RepaintManager.BUFFER_STRATEGY_TYPE = BUFFER_STRATEGY_SPECIFIED_OFF.

       At the same time, http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6209673 seems to be now fixed.

       This matters only if swing.bufferPerWindow = true and we don't invoke JComponent.getGraphics() directly.

       True double buffering is needed to eliminate tearing on blit-accelerated scrolling and to restore
       frame buffer content without the usual repainting, even when the EDT is blocked. */
    if (Patches.REPAINT_MANAGER_LEAK) {
      RepaintManager.setCurrentManager(new IdeRepaintManager());
    }

    if (SystemInfo.isXWindow) {
      String wmName = X11UiUtil.getWmName();
      LOG.info("WM detected: " + wmName);
      if (wmName != null) {
        X11UiUtil.patchDetectedWm(wmName);
      }
    }

    IconLoader.activate();

    new JFrame().pack(); // this peer will prevent shutting down our application
  }

  @NotNull
  public ApplicationStarter getStarter() {
    String key;
    if (myArgs.length > 0) {
      key = myArgs[0];
    }
    else {
      key = System.getProperty(ExtensionPoints.APPLICATION_STARTER);
    }
    if (key != null) {
      PluginManagerCore.getPlugins();

      ExtensionPoint<ApplicationStarter> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.APPLICATION_STARTER);
      ApplicationStarter[] starters = point.getExtensions();
      for (ApplicationStarter o : starters) {
        if (Comparing.equal(o.getCommandName(), key)) return o;
      }
    }

    return new IdeStarter(myPerformProjectLoad);
  }

  public void run() {
    try {
      ApplicationManagerEx.getApplicationEx().load();
      myLoaded = true;

      ((TransactionGuardImpl) TransactionGuard.getInstance()).performUserActivity(() -> myStarter.main(myArgs));
      myStarter = null; //GC it
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  static void initLAF() {
    try {
      Class.forName("com.jgoodies.looks.plastic.PlasticLookAndFeel");

      if (SystemInfo.isWindows) {
        UIManager.installLookAndFeel("JGoodies Windows L&F", "com.jgoodies.looks.windows.WindowsLookAndFeel");
      }

      UIManager.installLookAndFeel("JGoodies Plastic", "com.jgoodies.looks.plastic.PlasticLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Plastic 3D", "com.jgoodies.looks.plastic.Plastic3DLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Plastic XP", "com.jgoodies.looks.plastic.PlasticXPLookAndFeel");
    }
    catch (ClassNotFoundException ignored) { }
  }

  /**
   * Used for GUI tests to stop IdeEventQueue dispatching when Application is disposed already
   */
  public void shutdown() {
    myLoaded = false;
    IdeEventQueue.applicationClose();
    ShutDownTracker.getInstance().run();
  }

  @NotNull
  public String[] getCommandLineArguments() {
    return myArgs;
  }

  public void disableProjectLoad() {
    myPerformProjectLoad = false;
  }
}