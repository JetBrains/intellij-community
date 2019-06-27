// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author egor
 */
public class DebugAttachDetector {
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
        LOG.warn("Unable to start DebugAttachDetector, please add `--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED` to VM options");
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

    myTask = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> {
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

  @Nullable
  private static final String DEBUG_ARGS = getDebugArgs();

  private static String getDebugArgs() {
    return ContainerUtil.find(ManagementFactory.getRuntimeMXBean().getInputArguments(), s -> s.contains("-agentlib:jdwp"));
  }

  public static boolean isDebugEnabled() {
    return DEBUG_ARGS != null;
  }

  private static boolean isDebugServer() {
    return DEBUG_ARGS != null && DEBUG_ARGS.contains("server=y");
  }

  public static boolean isAttached() {
    if (!isDebugEnabled()) {
      return false;
    }
    if (!isDebugServer()) {
      return true;
    }
    Properties properties = ApplicationManager.getApplication().getComponent(DebugAttachDetector.class).myAgentProperties;
    if (properties == null) { // For now return true if can not detect
      return true;
    }
    return isAttached(properties);
  }
}
