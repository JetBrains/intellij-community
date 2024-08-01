// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.PluginException
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val LOG: Logger
  get() = logger<IdeStartupWizard>()

val isIdeStartupDialogEnabled: Boolean
  get() = !ApplicationManagerEx.isInIntegrationTest() &&
          System.getProperty("intellij.startup.dialog", "true").toBoolean()

val isIdeStartupWizardEnabled: Boolean
  get() = !ApplicationManagerEx.isInIntegrationTest() &&
          System.getProperty("intellij.startup.wizard", "true").toBoolean()

@ExperimentalCoroutinesApi
internal suspend fun runStartupWizard(isInitialStart: Job, app: Application) {
  LOG.info("Entering startup wizard workflow.")

  val point = app.extensionArea
    .getExtensionPoint<IdeStartupWizard>("com.intellij.ideStartupWizard") as ExtensionPointImpl<IdeStartupWizard>
  val sortedAdapters = point.sortedAdapters
  for (adapter in sortedAdapters) {
    val pluginDescriptor = adapter.pluginDescriptor
    if (!pluginDescriptor.isBundled) {
      LOG.error(PluginException("ideStartupWizard extension can be implemented only by a bundled plugin", pluginDescriptor.pluginId))
      continue
    }

    try {
      val wizard = adapter.createInstance<IdeStartupWizard>(app) ?: continue
      val timeoutMs = System.getProperty("intellij.startup.wizard.initial.timeout").orEmpty().toIntOrNull() ?: 2000

      span("app manager initial state waiting") {
        val startTimeNs = System.nanoTime()
        try {
          withTimeout(timeoutMs.milliseconds) {
            isInitialStart.join()
          }
          IdeStartupWizardCollector.logInitialStartSuccess()
        }
        catch (_: TimeoutCancellationException) {
          LOG.warn("Timeout on waiting for initial start, proceeding the startup flow without waiting.")
          IdeStartupWizardCollector.logInitialStartTimeout()
        }
        finally {
          IdeStartupWizardCollector.logStartupStageTime(
            StartupWizardStage.InitialStart,
            Duration.ofNanos(System.nanoTime() - startTimeNs)
          )
        }
      }

      LOG.info("Executing the onboarding flow for adapter $wizard.")
      span("${adapter.assignableToClassName}.run", Dispatchers.EDT) block@{
        val startupStatus = com.intellij.platform.ide.bootstrap.isInitialStart
        LOG.info("Inside the onboarding flow for adapter $wizard. StartupStatus.isCompleted: ${startupStatus?.isCompleted}")
        if (startupStatus?.isCompleted == false) {
          LOG.info("Initial startup initialization is not yet complete. Will continue to the wizard (if necessary)")
        }
        try {
          if (startupStatus != null && startupStatus.isCompleted && !startupStatus.isCancelled) {
            val wasSuccessful = startupStatus.getCompleted()
            if (!wasSuccessful) {
              LOG.info("Initial start was unsuccessful, terminating the wizard flow.")
              return@block
            }
          }
        } finally {
          startupStatus?.cancel()
        }

        if (isIdeStartupWizardEnabled) {
          LOG.info("Passing execution control to $wizard.")
          wizard.run()
        } else {
          LOG.info("Skipping the actual wizard call.")
        }
      }

      // first wizard wins
      break
    }
    catch (e: Throwable) {
      LOG.error(PluginException(e, pluginDescriptor.pluginId))
    }
  }
  point.reset()
}

@Internal
interface IdeStartupWizard {
  suspend fun run()
}

enum class StartupWizardStage {
  InitialStart,
  ProductChoicePage,
  SettingsToSyncPage,
  SettingsToImportPage,
  ImportProgressPage,
  WizardThemePage,
  WizardKeymapPage,
  WizardPluginPage,
  WizardProgressPage
}

@Internal
object IdeStartupWizardCollector : CounterUsagesCollector() {
  val GROUP = EventLogGroup("wizard.startup", 7)
  override fun getGroup() = GROUP

  private val initialStartSucceeded = GROUP.registerEvent("initial_start_succeeded")
  fun logInitialStartSuccess() {
    initialStartSucceeded.log()
  }

  private val initialStartTimeoutTriggered = GROUP.registerEvent("initial_start_timeout_triggered")
  fun logInitialStartTimeout() {
    initialStartTimeoutTriggered.log()
  }

  private val experimentState = GROUP.registerEvent(
    "initial_start_experiment_state",
    EventFields.Enum<IdeStartupExperiment.GroupKind>("kind"),
    EventFields.Int("group"),
    EventFields.Boolean("enabled")
  )

  internal fun logWizardExperimentState() {
    assert(ConfigImportHelper.isFirstSession())
    val isEnabled = IdeStartupExperiment.isWizardExperimentEnabled()
    LOG.info("IDE startup isEnabled = $isEnabled," +
             " IDEStartupKind = ${IdeStartupExperiment.experimentGroupKind}, " +
             "IDEStartup = ${IdeStartupExperiment.experimentGroup}")
    experimentState.log(
      IdeStartupExperiment.experimentGroupKind,
      IdeStartupExperiment.experimentGroup,
      isEnabled
    )
  }

  private val wizardStageEnded = GROUP.registerEvent(
    "wizard_stage_ended",
    EventFields.Enum<StartupWizardStage>("stage"),
    EventFields.DurationMs
  )

  fun logStartupStageTime(stage: StartupWizardStage, duration: Duration) {
    wizardStageEnded.log(stage, duration.toMillis())
  }
}
