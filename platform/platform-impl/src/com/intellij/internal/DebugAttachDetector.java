// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.DebugAttachDetectorArgs;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class DebugAttachDetector {
  private static final Logger LOG = Logger.getInstance(DebugAttachDetector.class);
  private Properties myAgentProperties = null;

  private ScheduledFuture<?> myTask;
  private boolean myAttached;
  private boolean myReady;

  public DebugAttachDetector() {
    Class<?> vmSupportClass;
    try {
      vmSupportClass = Class.forName("jdk.internal.vm.VMSupport");
    }
    catch (Exception e) {
      try {
        vmSupportClass = Class.forName("sun.misc.VMSupport");
      }
      catch (Exception ignored) {
        LOG.warn("Unable to init DebugAttachDetector, VMSupport class not found");
        return;
      }
    }

    Application app = ApplicationManager.getApplication();
    try {
      myAgentProperties = (Properties)vmSupportClass.getMethod("getAgentProperties").invoke(null);
    }
    catch (NoSuchMethodException | InvocationTargetException ex) {
      LOG.error(ex);
    }
    catch (IllegalAccessException ex) {
      if (app.isInternal() && !PluginManagerCore.isRunningFromSources()) {
        LOG.warn("Unable to start DebugAttachDetector, please add `--add-exports java.base/jdk.internal.vm=ALL-UNNAMED` to VM options");
      }
    }

    if (myAgentProperties == null ||
        !app.isInternal() ||
        app.isUnitTestMode() ||
        Boolean.getBoolean("disable.attach.detector") ||
        PluginManagerCore.isRunningFromSources() ||
        !isDebugEnabled()) {
      return;
    }

    myTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
      boolean attached = isAttached(myAgentProperties);
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

  private static boolean isAttached(@NotNull Properties properties) {
    String property = properties.getProperty("sun.jdwp.listenerAddress");
    return property != null && property.isEmpty();
  }

  public static boolean isDebugEnabled() {
    return DebugAttachDetectorArgs.getDebugArgs() != null;
  }

  private static boolean isDebugServer() {
    String args = DebugAttachDetectorArgs.getDebugArgs();
    return args != null && args.contains("server=y");
  }

  public static boolean isAttached() {
    if (!isDebugEnabled()) {
      return false;
    }
    if (!isDebugServer()) {
      return true;
    }
    Properties properties = ApplicationManager.getApplication().getService(DebugAttachDetector.class).myAgentProperties;
    // for now, return true if you can not detect
    if (properties == null) {
      return true;
    }
    return isAttached(properties);
  }
}
