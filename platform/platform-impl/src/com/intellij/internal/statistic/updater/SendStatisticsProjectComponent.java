/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.internal.statistic.updater;

import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.internal.statistic.connect.RemotelyConfigurableStatisticsService;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.connect.StatisticsHttpClientSender;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public class SendStatisticsProjectComponent implements ProjectComponent {
  private Project myProject;
  private Alarm myAlarm;

  private static int DELAY_IN_MIN = 10;

  public SendStatisticsProjectComponent(Project project) {
    myProject = project;
    myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, myProject);
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
          runStatisticsService();
      }
    });
  }

  private void runStatisticsService() {
    final RemotelyConfigurableStatisticsService statisticsService =
      new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(),
                                                new StatisticsHttpClientSender(),
                                                new StatisticsUploadAssistant());
    if (StatisticsUploadAssistant.showNotification()) {
      StatisticsNotificationManager.showNotification(statisticsService, myProject);
    }
    else {
      if (StatisticsUploadAssistant.isSendAllowed() && StatisticsUploadAssistant.isTimeToSend()) {
        runWithDelay(statisticsService);
      }
    }
  }

  private void runWithDelay(final @NotNull RemotelyConfigurableStatisticsService statisticsService) {
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (DumbService.isDumb(myProject)) {
           runWithDelay(statisticsService);
        } else {
          statisticsService.send();
        }
      }
    }, DELAY_IN_MIN * 60 * 1000);
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return SendStatisticsProjectComponent.class.getName();
  }
}
