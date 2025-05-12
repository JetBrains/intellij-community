// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Future;

@ApiStatus.Internal
public final class Helpers {
  private static final Logger LOG = Logger.getInstance(Helpers.class);
  private static final String MODEL_ID_PREFIX = "Model Identifier:";
  private static final @NonNls String TB_SERVER_PROCESS = "TouchBarServer";
  private static final boolean FORCE_PHYSICAL_ESC = Boolean.getBoolean("touchbar.physical.esc");
  private static Boolean ourIsPhysicalEsc = null;
  private static Future<?> ourIsPhysicalEscFuture = null;

  static void emulateKeyPress(int javaKeyEventCode) {
    EventQueue systemEventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    systemEventQueue.postEvent(
      new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, javaKeyEventCode, KeyEvent.CHAR_UNDEFINED)
    );
    systemEventQueue.postEvent(
      new KeyEvent(new JPanel(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, javaKeyEventCode, KeyEvent.CHAR_UNDEFINED)
    );
  }

  @VisibleForTesting
  public static boolean isTouchBarServerRunning() {
    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", TB_SERVER_PROCESS)
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.SYSTEM);
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      return !out.getStdout().isEmpty();
    }
    catch (ExecutionException e) {
      LOG.debug(e);
    }
    return false;
  }

  // returns true on success
  public static boolean restartTouchBarServer() {
    try {
      final GeneralCommandLine cmdLine = new GeneralCommandLine("pkill", TB_SERVER_PROCESS)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.SYSTEM);
      final ProcessOutput out = ExecUtil.sudoAndGetOutput(cmdLine, "");
      return out.getStderr().isEmpty();
    }
    catch (ExecutionException | IOException e) {
      LOG.debug(e);
    }

    return false;
  }

  public static String getAppId() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return null;
    }

    String appId;
    final ID nativePool = Foundation.invoke("NSAutoreleasePool", "new");
    try {
      final ID bundle = Foundation.invoke("NSBundle", "mainBundle");
      final ID dict = Foundation.invoke(bundle, "infoDictionary");
      final ID nsAppID = Foundation.invoke(dict, "objectForKey:", Foundation.nsString("CFBundleIdentifier"));
      appId = Foundation.toStringViaUTF8(nsAppID);
    }
    finally {
      Foundation.invoke(nativePool, "release");
    }

    if (appId == null || appId.isEmpty()) {
      LOG.error("can't obtain application id from NSBundle.mainBundle");
    }

    return appId;
  }

  public static boolean isPhisycalEsc() {
    if (FORCE_PHYSICAL_ESC) {
      return true;
    }

    if (ourIsPhysicalEsc != null) {
      return ourIsPhysicalEsc;
    }

    if (ourIsPhysicalEscFuture != null) {
      if (!ourIsPhysicalEscFuture.isDone()) {
        return false;
      }
      ourIsPhysicalEscFuture = null;
      return ourIsPhysicalEsc;
    }

    // system_profiler SPHardwareDataType
    //
    // output example:
    //
    //Hardware:
    //
    //Hardware Overview:
    //
    //Model Name: MacBook Pro
    //Model Identifier: MacBookPro14,3
    //Processor Name: Quad-Core Intel Core i7
    //Processor Speed: 2,9 GHz
    //Number of Processors: 1
    //Total Number of Cores: 4
    //L2 Cache (per Core): 256 KB
    //L3 Cache: 8 MB
    //Hyper-Threading Technology:
    //Enabled
    //Memory: 16 GB
    //Boot ROM Version: 204.0.0.0.0
    //SMC Version (system): 2.45f1
    //Serial Number (system): C02VW52PHTD6
    //Hardware UUID: 263EC2DC-9078-549E-9856-EF94D704E3A6

    // can reimplement with use of:
    // system_profiler SPHardwareDataType | awk '/Model Identifier/ {print $3}'

    ourIsPhysicalEsc = false;

    final @NotNull Application app = ApplicationManager.getApplication();
    ourIsPhysicalEscFuture = app.executeOnPooledThread(() -> {
      final GeneralCommandLine cmdLine = new GeneralCommandLine("system_profiler", TB_SERVER_PROCESS);
      cmdLine.addParameter("SPHardwareDataType");
      try {
        final ProcessOutput out =
          ExecUtil.execAndGetOutput(cmdLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.SYSTEM));
        LOG.debug("SPHardwareDataType output:");
        for (String line : out.getStdoutLines(true)) {
          LOG.debug("\t" + line);
          String tline = line.trim();
          if (tline.startsWith(MODEL_ID_PREFIX)) {
            // FIXME: need to get output for 16-inch macbook and ensure correctness
            String model = tline.substring(MODEL_ID_PREFIX.length()).trim();
            ourIsPhysicalEsc = model.contains("16") || model.equals("MacBookPro17,1");
          }
        }
        LOG.debug("\tourIsPhysicalEsc=" + ourIsPhysicalEsc);
      }
      catch (ExecutionException e) {
        LOG.debug(e);
      }
      return ourIsPhysicalEsc;
    });

    return false;
  }

  //
  // Other
  //

  static @Nullable Component getCurrentFocusComponent() {
    final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner = focusManager.getFocusOwner();
    if (focusOwner == null) {
      focusOwner = focusManager.getPermanentFocusOwner();
    }
    if (focusOwner == null) {
      return focusManager.getFocusedWindow();
    }
    return focusOwner;
  }

  static @NotNull String getActionId(@NotNull AnAction action) {
    String actionId = ActionManager.getInstance().getId(action instanceof CustomisedActionGroup o ? o.getDelegate() : action);
    return actionId == null ? action.toString() : actionId;
  }

  static void collectLeafActions(@NotNull ActionGroup actionGroup, @NotNull Collection<? super AnAction> out) {
    AnAction[] actions = actionGroup.getChildren(null);
    for (AnAction childAction : actions) {
      if (childAction == null) {
        continue;
      }
      if (childAction instanceof ActionGroup childGroup) {
        collectLeafActions(childGroup, out);
        continue;
      }
      if (childAction instanceof Separator) {
        continue;
      }
      out.add(childAction);
    }
  }
}