// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.logging.*;

public class LogFocusRequestsAction extends ToggleAction implements DumbAware {
  private static final Key<Boolean> ENABLED = Key.create("LogFocusRequestsAction.enabled");

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(SystemInfo.isJetBrainsJvm);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return Boolean.TRUE.equals(ApplicationManager.getApplication().getUserData(ENABLED));
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    ApplicationManager.getApplication().putUserData(ENABLED, state ? Boolean.TRUE : null);
    Logger logger = LogManager.getLogManager().getLogger("jb.focus.requests");
    if (logger != null) {
      logger.setLevel(state ? Level.ALL : Level.INFO);
      if (state) {
        logger.addHandler(LogHandler.INSTANCE);
      }
      else {
        logger.removeHandler(LogHandler.INSTANCE);
      }
    }
    FocusManagerImpl.FOCUS_REQUESTS_LOG.setLevel(state ? LogLevel.DEBUG : LogLevel.INFO);
  }

  private static class LogHandler extends Handler {
    private static final LogHandler INSTANCE = new LogHandler();

    @Override
    public void publish(LogRecord record) {
      FocusManagerImpl.FOCUS_REQUESTS_LOG.debug(record.getMessage(), record.getThrown());
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
