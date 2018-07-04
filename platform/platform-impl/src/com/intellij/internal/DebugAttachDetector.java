// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import sun.misc.VMSupport;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author egor
 */
public class DebugAttachDetector {
  private static final Logger LOG = Logger.getInstance(DebugAttachDetector.class);

  private String myHost;
  private int myPort = -1;
  private ScheduledFuture<?> myTask;
  private boolean myAttached;
  private boolean myReady;

  public static Pair<String, Integer> getAttachAddress(List<String> arguments) {
    String host = null;
    int port = -1;
    for (String argument : arguments) {
      if (argument.startsWith("-agentlib:jdwp") && argument.contains("transport=dt_socket")) {
        String[] params = argument.split(",");
        for (String param : params) {
          if (param.startsWith("address")) {
            try {
              String[] address = param.split("=")[1].split(":");
              if (address.length == 1) {
                port = Integer.parseInt(address[0]);
              }
              else {
                host = address[0];
                port = Integer.parseInt(address[1]);
              }
            }
            catch (Exception e) {
              LOG.error(e);
              return null;
            }
            break;
          }
        }
        break;
      }
    }
    if (port > -1) {
      return Pair.create(host, port);
    }
    return null;
  }

  public DebugAttachDetector() {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (!app.isInternal()
        || app.isUnitTestMode()
        || app.isHeadlessEnvironment()
        || Boolean.getBoolean("disable.attach.detector")
        || PluginManagerCore.isRunningFromSources()) return;

    Pair<String, Integer> attachAddress = getAttachAddress(ManagementFactory.getRuntimeMXBean().getInputArguments());
    if (attachAddress == null) return;
    myHost = attachAddress.first;
    myPort = attachAddress.second;

    myTask = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> {
      String property = VMSupport.getAgentProperties().getProperty("sun.jdwp.listenerAddress");

      // leads to garbage in IDEA console, see IDEA-158940
      // boolean attached = !NetUtils.canConnectToRemoteSocket(myHost, myPort);

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
