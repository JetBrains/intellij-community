// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.io.IOException;

public class Utils {
  private static final Logger LOG = Logger.getInstance(Utils.class);

  private static final String TB_SERVER_PROCESS = SystemInfo.isMacOSHighSierra ? "TouchBarServer" : "TouchBarAgent";

  static boolean isTouchBarServerRunning() {
    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", TB_SERVER_PROCESS);
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      return !out.getStdout().isEmpty();
    } catch (ExecutionException e) {
      LOG.error(e);
    }
    return false;
  }

  static void restartServer() {
    try {
      ExecUtil.sudo(new GeneralCommandLine("pkill", TB_SERVER_PROCESS), "");
    } catch (ExecutionException e) {
      LOG.error(e);
    } catch (IOException e) {
      LOG.error(e);
    }
  }
}
