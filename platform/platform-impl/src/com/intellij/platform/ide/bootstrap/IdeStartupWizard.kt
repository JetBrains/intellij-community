// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.PluginException
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.util.MathUtil
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val log = logger<IdeStartupWizard>()

val isIdeStartupWizardEnabled: Boolean
  get() = !ApplicationManagerEx.isInIntegrationTest()
          && System.getProperty ("intellij.startup.wizard", "true").toBoolean()
          && IdeStartupExperiment.isExperimentEnabled()

@ExperimentalCoroutinesApi
internal suspend fun runStartupWizard(isInitialStart: Job, app: Application) {

  log.info("Entering startup wizard workflow.")

  val point = app.extensionArea
    .getExtensionPoint<IdeStartupWizard>("com.intellij.ideStartupWizard") as ExtensionPointImpl<IdeStartupWizard>
  val sortedAdapters = point.sortedAdapters
  for (adapter in sortedAdapters) {
    val pluginDescriptor = adapter.pluginDescriptor
    if (!pluginDescriptor.isBundled) {
      log.error(PluginException("ideStartupWizard extension can be implemented only by a bundled plugin", pluginDescriptor.pluginId))
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
          log.warn("Timeout on waiting for initial start, proceeding the startup flow without waiting.")
          IdeStartupWizardCollector.logInitialStartTimeout()
        }
        finally {
          IdeStartupWizardCollector.logStartupStageTime(
            StartupWizardStage.InitialStart,
            Duration.ofNanos(System.nanoTime() - startTimeNs)
          )
        }
      }

      log.info("Passing execution control to $wizard.")
      span("${adapter.assignableToClassName}.run", Dispatchers.EDT) block@{
        val startupStatus = com.intellij.platform.ide.bootstrap.isInitialStart
        try {
          if (startupStatus != null && startupStatus.isCompleted && !startupStatus.isCancelled) {
            val wasSuccessful = startupStatus.getCompleted()
            if (!wasSuccessful) {
              log.info("Initial start was unsuccessful, terminating the wizard flow.")
              return@block
            }
          }
        } finally {
          startupStatus?.cancel()
        }

        log.info("Passing execution control to $wizard.")
        wizard.run()
      }

      // first wizard wins
      break
    }
    catch (e: Throwable) {
      log.error(PluginException(e, pluginDescriptor.pluginId))
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
  WizardPluginPage
}

object IdeStartupWizardCollector : CounterUsagesCollector() {

  val GROUP = EventLogGroup("wizard.startup", 4)
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
  fun logExperimentState() {
    if (ConfigImportHelper.isFirstSession()) {
      val isEnabled = IdeStartupExperiment.isExperimentEnabled()
      log.info("IDE startup isEnabled = $isEnabled, IDEStartupKind = ${IdeStartupExperiment.experimentGroupKind}, IDEStartup = ${IdeStartupExperiment.experimentGroup}")
      experimentState.log(
        IdeStartupExperiment.experimentGroupKind,
        IdeStartupExperiment.experimentGroup,
        isEnabled
      )
    }
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

object IdeStartupExperiment {

  enum class GroupKind {
    Experimental,
    Control,
    Undefined
  }

  @Suppress("DEPRECATION")
  private val numberOfGroups = when {
    PlatformUtils.isIdeaUltimate() || PlatformUtils.isPyCharmPro() -> 10
    else -> 3
  }

  @Suppress("DEPRECATION")
  private fun getGroupKind(group: Int) = when {
    PlatformUtils.isIdeaUltimate() || PlatformUtils.isPyCharmPro() -> when {
      group >= 0 && group <= 7 -> GroupKind.Experimental
      group == 8 || group == 9 -> GroupKind.Control
      else -> GroupKind.Undefined
    }
    else -> when (group) {
      0, 1 -> GroupKind.Experimental
      2 -> GroupKind.Control
      else -> GroupKind.Undefined
    }
  }

  private fun String.asBucket() = MathUtil.nonNegativeAbs(this.hashCode()) % 256
  private fun getBucket(): Int {
    val deviceId = log.runAndLogException {
      DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "FUS")
    } ?: return 0
    return deviceId.asBucket()
  }

  val experimentGroup by lazy {
    val registryExperimentGroup = (System.getProperty("ide.transfer.wizard.experiment.group", "-1").toIntOrNull() ?: -1)
      .coerceIn(-1, numberOfGroups - 1)
    if (registryExperimentGroup >= 0) return@lazy registryExperimentGroup

    val bucket = getBucket()
    val experimentGroup = bucket % numberOfGroups
    experimentGroup
  }

  val experimentGroupKind by lazy {
    getGroupKind(experimentGroup)
  }

  fun isExperimentEnabled(): Boolean {
    return when (experimentGroupKind) {
      GroupKind.Experimental -> true
      GroupKind.Control -> false
      GroupKind.Undefined -> true
    }
  }
}