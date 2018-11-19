// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import sun.misc.VMSupport;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author egor
 */
public class DebugAttachDetector {
  private ScheduledFuture<?> myTask;
  private boolean myAttached;
  private boolean myReady;

  public DebugAttachDetector() {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (!app.isInternal()
        || app.isUnitTestMode()
        || app.isHeadlessEnvironment()
        || Boolean.getBoolean("disable.attach.detector")
        || PluginManagerCore.isRunningFromSources()) return;

    if (ManagementFactory.getRuntimeMXBean().getInputArguments().stream().noneMatch(s -> s.contains("-agentlib:jdwp"))) {
      return;
    }

    myTask = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> {
      String property = VMSupport.getAgentProperties().getProperty("sun.jdwp.listenerAddress");
      boolean attached = property != null && property.isEmpty();
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
}
