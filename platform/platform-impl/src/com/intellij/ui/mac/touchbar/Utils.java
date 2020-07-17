// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;

import java.io.IOException;

public final class Utils {
  private static final Logger LOG = Logger.getInstance(Utils.class);
  private static final String TB_SERVER_PROCESS = "TouchBarServer";

  public static boolean isTouchBarServerRunning() {
    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", TB_SERVER_PROCESS)
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.SYSTEM);
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      return !out.getStdout().isEmpty();
    }
    catch (ExecutionException e) {
      LOG.error(e);
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
      LOG.error(e);
    }

    return false;
  }

  public static String getAppId() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return null;
    }

    String appId;
    try (@SuppressWarnings("unused") NSAutoreleaseLock lock = new NSAutoreleaseLock()) {
      final ID bundle = Foundation.invoke("NSBundle", "mainBundle");
      final ID dict = Foundation.invoke(bundle, "infoDictionary");
      final ID nsAppID = Foundation.invoke(dict, "objectForKey:", Foundation.nsString("CFBundleIdentifier"));
      appId = Foundation.toStringViaUTF8(nsAppID);
    }

    if (appId == null || appId.isEmpty()) {
      LOG.error("can't obtain application id from NSBundle.mainBundle");
    }

    return appId;
  }
}