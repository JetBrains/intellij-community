package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.PerformInBackgroundOption;

public class BackgroundFromStartOption implements PerformInBackgroundOption {
  private final static BackgroundFromStartOption ourInstance = new BackgroundFromStartOption();

  public static PerformInBackgroundOption getInstance() {
    return ourInstance;
  }

  public boolean shouldStartInBackground() {
    return true;
  }

  public void processSentToBackground() {
  }

}
