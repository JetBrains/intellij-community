/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal;

import com.intellij.concurrency.JobScheduler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.NetUtils;

import java.lang.management.ManagementFactory;
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

  public DebugAttachDetector() {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (!app.isInternal()
        || app.isUnitTestMode()
        || app.isHeadlessEnvironment()
        || "true".equals(System.getProperty("idea.debug.mode"))) return;

    for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if (argument.startsWith("-agentlib:jdwp") && argument.contains("transport=dt_socket")) {
        String[] params = argument.split(",");
        for (String param : params) {
          if (param.startsWith("address")) {
            try {
              String[] address = param.split("=")[1].split(":");
              if (address.length == 1) {
                myPort = Integer.parseInt(address[0]);
              }
              else {
                myHost = address[0];
                myPort = Integer.parseInt(address[1]);
              }
            }
            catch (Exception e) {
              LOG.error(e);
              return;
            }
            break;
          }
        }
        break;
      }
    }

    if (myPort < 0) return;

    myTask = JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          boolean attached = !NetUtils.canConnectToRemoteSocket(myHost, myPort);
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
        }
      }, 5, 5, TimeUnit.SECONDS);
  }
}
