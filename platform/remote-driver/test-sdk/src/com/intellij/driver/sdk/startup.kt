package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Remote("com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurerService")
interface FUSProjectHotStartUpMeasurerService {
  fun isHandlingFinished(): Boolean
}

/**
 * For production, it's perfectly fine not so send
 * startup statistics from [com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer]
 * when a project was closed during it's opening. But to test such cases, one must enforce that statistic to be written.
 * This is util method for such enforcement
 */
fun Driver.waitForStartupFUSToWrite(timeout: Duration = 10.seconds) {
  waitFor("Fus startup is finished", timeout = timeout, interval = 1.seconds) {
    service(FUSProjectHotStartUpMeasurerService::class).isHandlingFinished()
  }
}

@Remote("com.intellij.openapi.startup.StartupManager")
interface StartupManager {
  fun postStartupActivityPassed(): Boolean
}

/**
 * Waits until the startup manager of [project] has dispatched every post-startup activity: the `ProjectActivity` extensions
 * are launched, and the dumb-aware and the dynamically registered activities have run. A launched activity may still run,
 * and some live as long as the project, so no wait covers them all; [waitForIndicators] with its quiet period covers the
 * ones that show progress.
 */
fun Driver.waitForPostStartupActivities(project: Project, timeout: Duration = 1.minutes) {
  waitFor("Post-startup activities passed", timeout = timeout) {
    service(StartupManager::class, project).postStartupActivityPassed()
  }
}

/** [waitForPostStartupActivities] of the single project, once a project is opened: the launch alone does not wait for that. */
fun Driver.waitForPostStartupActivities(timeout: Duration = 1.minutes) {
  waitForProjectOpen(timeout)
  waitForPostStartupActivities(singleProject(), timeout)
}