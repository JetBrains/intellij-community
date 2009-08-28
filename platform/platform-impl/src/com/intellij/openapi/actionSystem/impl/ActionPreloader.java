package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.project.DumbAwareRunnable;

/**
 * @author yole
 */
public class ActionPreloader {
  public ActionPreloader(StartupManager manager) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      manager.registerPostStartupActivity(new DumbAwareRunnable() {
        public void run() {
          ((ActionManagerImpl)ActionManager.getInstance()).preloadActions();
        }
      });
    }
  }
}
