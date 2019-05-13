// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class Utils {
  private static final Logger LOG = Logger.getInstance(Utils.class);
  private static final String TB_SERVER_PROCESS = SystemInfo.isMacOSHighSierra ? "TouchBarServer" : "TouchBarAgent";

  public static boolean isTouchBarServerRunning() {
    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", TB_SERVER_PROCESS);
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      return !out.getStdout().isEmpty();
    } catch (ExecutionException e) {
      LOG.error(e);
    }
    return false;
  }

  // returns true when success
  public static boolean restartTouchBarServer() {
    try {
      final ProcessOutput out = ExecUtil.sudoAndGetOutput(new GeneralCommandLine("pkill", TB_SERVER_PROCESS), "");
      return out.getStderr().isEmpty();
    } catch (ExecutionException e) {
      LOG.error(e);
    } catch (IOException e) {
      LOG.error(e);
    }

    return false;
  }

  public static String getAppId() {
    try {
      final ApplicationInfoEx appEx = ApplicationInfoImpl.getInstanceEx();
      if (appEx == null) // unit-test case
        return null;
    } catch (Throwable e) {
      LOG.debug(e); // no application
      return null;
    }

    String appId = null;
    try (NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
      final ID bundle = Foundation.invoke("NSBundle", "mainBundle");
      final ID dict = Foundation.invoke(bundle, "infoDictionary");
      final ID nsAppID = Foundation.invoke(dict, "objectForKey:", Foundation.nsString("CFBundleIdentifier"));
      appId = Foundation.toStringViaUTF8(nsAppID);
    }

    if (appId == null || appId.isEmpty())
      LOG.error("can't obtain application id from NSBundle.mainBundle");

    return appId;
  }

  static @Nullable
  ModalityState getCurrentModalityState() { return ApplicationManager.getApplication() != null ? LaterInvocator.getCurrentModalityState() : null; }
}
