// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

import java.util.Map;

public interface ProcessService {
  Process startPtyProcess(String[] command,
                          String directory,
                          Map<String, String> env,
                          PtyCommandLineOptions options,
                          Application app,
                          boolean redirectErrorStream,
                          boolean windowsAnsiColorEnabled,
                          boolean unixOpenTtyToPreserveOutputAfterTermination);

  boolean sendWinProcessCtrlC(Process process);

  void killWinProcessRecursively(Process process);

  boolean isWinPty(Process process);

  Integer winPtyChildProcessId(Process process);

  static ProcessService getInstance() {
    return ApplicationManager.getApplication().getService(ProcessService.class);
  }
}