package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;

import java.util.List;

@Remote("com.intellij.openapi.wm.ex.StatusBarEx")
public interface StatusBar {
  List<TaskInfoPair> getBackgroundProcesses();

  boolean isProcessWindowOpen();

  @Remote("com.intellij.openapi.util.Pair")
  interface TaskInfoPair {
    TaskInfo getFirst();

    ProgressIndicator getSecond();
  }
}
