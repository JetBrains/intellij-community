// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.updater;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StatisticsStateCollectorsScheduler implements ApplicationInitializedListener {
  public static final int LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN = 10;
  public static final int LOG_APPLICATION_STATES_DELAY_IN_MIN = 24 * 60;
  public static final int LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN = 15;
  public static final int LOG_PROJECTS_STATES_DELAY_IN_MIN = 12 * 60;

  private static final Map<Project, Future<?>> myPersistStatisticsSessionsMap = Collections.synchronizedMap(new HashMap<>());

  @Override
  public void componentsInitialized() {
    runStatesLogging();
  }

  private static void runStatesLogging() {
    if (!StatisticsUploadAssistant.isSendAllowed()) return;
    JobScheduler.getScheduler().scheduleWithFixedDelay(() -> FUStateUsagesLogger.create().logApplicationStates(),
                                                       LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN,
                                                       LOG_APPLICATION_STATES_DELAY_IN_MIN, TimeUnit.MINUTES);

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        ScheduledFuture<?> scheduledFuture = JobScheduler.getScheduler().schedule(() -> {
          //wait until initial indexation will be finished
          DumbService.getInstance(project).runWhenSmart(() -> {
            ScheduledFuture<?> future = JobScheduler.getScheduler()
              .scheduleWithFixedDelay(() -> FUStateUsagesLogger.create().logProjectStates(project, new EmptyProgressIndicator()),
                                      0, LOG_PROJECTS_STATES_DELAY_IN_MIN, TimeUnit.MINUTES);
            myPersistStatisticsSessionsMap.put(project, future);
          });
        }, LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN, TimeUnit.MINUTES);
        myPersistStatisticsSessionsMap.put(project, scheduledFuture);
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        Future<?> future = myPersistStatisticsSessionsMap.remove(project);
        if (future != null) {
          future.cancel(true);
        }
      }
    });
  }
}
