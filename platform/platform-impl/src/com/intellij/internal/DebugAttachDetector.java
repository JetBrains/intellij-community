// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.DebugAttachDetectorArgs;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.TimeUnit;

public final class DebugAttachDetector {
  private boolean myAttached;
  private boolean myReady;

  public DebugAttachDetector() {
    Application app = ApplicationManager.getApplication();
    if (!DebugAttachDetectorArgs.canDetectAttach() ||
        !app.isInternal() ||
        app.isUnitTestMode() ||
        Boolean.getBoolean("disable.attach.detector") ||
        PluginManagerCore.isRunningFromSources() ||
        !isDebugEnabled()) {
      return;
    }

    AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
      boolean attached = isAttached();
      if (!myReady) {
        myAttached = attached;
        myReady = true;
      }
      else if (attached != myAttached) {
        myAttached = attached;
        Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                                  "Remote debugger",
                                                  myAttached ? "attached" : "detached",
                                                  NotificationType.WARNING));
      }
    }, 5, 5, TimeUnit.SECONDS);
  }

  public static boolean isDebugEnabled() {
    return DebugAttachDetectorArgs.isDebugEnabled();
  }

  public static boolean isAttached() {
    return DebugAttachDetectorArgs.isAttached();
  }
}
