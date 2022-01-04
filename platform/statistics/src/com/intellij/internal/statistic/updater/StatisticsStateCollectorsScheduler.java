// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class StatisticsStateCollectorsScheduler implements ApplicationInitializedListener {
  public static final int LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN = 10;
  public static final int LOG_APPLICATION_STATES_DELAY_IN_MIN = 24 * 60;
  private static final int LOG_APPLICATION_STATE_SMART_MODE_DELAY_IN_SECONDS = 60;
  public static final int LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN = 5;
  public static final int LOG_PROJECTS_STATES_DELAY_IN_MIN = 12 * 60;

  private static final Map<Project, Future<?>> myPersistStatisticsSessionsMap = Collections.synchronizedMap(new HashMap<>());
  private static final OneTimeLogger myOneTimeLogger = new OneTimeLogger();

  @Override
  public void componentsInitialized() {
    runStatesLogging();
  }

  private static void runStatesLogging() {
    if (!StatisticsUploadAssistant.isSendAllowed()) return;

    // avoid overlapping logging from periodic scheduler and OneTimeLogger (long indexing case)
    JobScheduler.getScheduler().schedule(() -> myOneTimeLogger.allowExecution.set(false),
                                         LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN, TimeUnit.MINUTES);

    JobScheduler.getScheduler().scheduleWithFixedDelay(() -> FUStateUsagesLogger.create().logApplicationStates(),
                                                       LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN,
                                                       LOG_APPLICATION_STATES_DELAY_IN_MIN, TimeUnit.MINUTES);

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        // Smart mode is not available when LightEdit is active
        if (LightEdit.owns(project)) {
          return;
        }

        //wait until initial indexation will be finished
        DumbService.getInstance(project).runWhenSmart(() -> {
          ScheduledFuture<?> future = JobScheduler.getScheduler()
            .scheduleWithFixedDelay(() -> FUStateUsagesLogger.create().logProjectStates(project, new EmptyProgressIndicator()),
                                    LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN, LOG_PROJECTS_STATES_DELAY_IN_MIN, TimeUnit.MINUTES);
          myPersistStatisticsSessionsMap.put(project, future);
        });

        if (myOneTimeLogger.allowExecution.get()) {
          DumbService.getInstance(project).runWhenSmart(() -> {
            // wait until all projects will exit dumb mode
            if (ContainerUtil.exists(ProjectManager.getInstance().getOpenProjects(),
                                     p -> !p.isDisposed() && p.isInitialized() && DumbService.getInstance(p).isDumb())) {
              return;
            }
            myOneTimeLogger.scheduleLogging();
          });
        }
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

  private static class OneTimeLogger {
    private final AtomicBoolean allowExecution = new AtomicBoolean(true);

    public void scheduleLogging() {
      // Check and execute only once because several projects can exit dumb mode at the same time
      if (allowExecution.getAndSet(false)) {
        JobScheduler.getScheduler().schedule(() -> FUStateUsagesLogger.create().logApplicationStatesOnStartup(),
                                             LOG_APPLICATION_STATE_SMART_MODE_DELAY_IN_SECONDS, TimeUnit.SECONDS);
      }
    }
  }
}
