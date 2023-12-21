// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.activities.WritableDatabaseBackedTimeSpanUserActivity
import com.intellij.ae.database.dbs.timespan.TimeSpanUserActivityDatabaseManualKind
import com.intellij.ae.database.runUpdateEvent
import com.intellij.ae.database.utils.InstantUtils
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.execution.ExecutionListener
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.sqlite.ObjectBinderFactory
import java.time.Instant

/**
 * Writes events related to run configurations.
 */
object RunConfigurationTimeSpanUserActivity : WritableDatabaseBackedTimeSpanUserActivity() {
  override val canBeStale = true
  // TODO add flag 'errorProne' that fill turn off logs about bad end?
  override val id = "runconfig.running"

  // TODO pass time from build infra?
  internal suspend fun writeRunConfigurationStart(kind: RunConfigurationEventKind, id: Int) {
    val data = mapOf("act" to kind.eventName)
    submitManual(id.toString(), TimeSpanUserActivityDatabaseManualKind.Start, data)
  }

  internal suspend fun writeEnd(id: Int) {
    submitManual(id.toString(), TimeSpanUserActivityDatabaseManualKind.End, null)
  }

  /**
   *  @return sum of sessions lengths of specific [kind] in the period [from]..[until] in seconds
   */
  suspend fun getSessionsLength(kind: RunConfigurationEventKind, from: Instant, until: Instant): Int? {
    return getDatabase().execute { database ->
      val sessionsLengthStatement = database
        .prepareStatement("SELECT SUM(strftime('%s', ended_at) - strftime('%s', started_at)) FROM timespanUserActivity\n" +
                          "WHERE activity_id = '$id' " +
                          "AND json_extract(extra, '\$.act') = ? " +
                          "AND datetime(started_at) >= datetime(?) " +
                          "AND datetime(ended_at) <= datetime(?)", ObjectBinderFactory.create3<String, String, String>())
      sessionsLengthStatement.binder.bind(kind.eventName, InstantUtils.formatForDatabase(from), InstantUtils.formatForDatabase(until))
      sessionsLengthStatement.selectInt() ?: 0
    }
  }

  /**
   *  @return sum of sessions lengths grouped by kind in the period [from]..[until] in seconds
   */
  suspend fun getAllSessionsLength(from: Instant, until: Instant): Map<RunConfigurationEventKind, Int>? {
    return getDatabase().execute { database ->
      val allSessionsLengthStatement = database
        .prepareStatement("SELECT json_extract(extra, '\$.act') as field, SUM(strftime('%s', ended_at) - strftime('%s', started_at)) FROM timespanUserActivity\n" +
                          "WHERE activity_id = '$id' " +
                          "AND datetime(started_at) >= datetime(?) " +
                          "AND datetime(ended_at) <= datetime(?) " +
                          "GROUP BY field", ObjectBinderFactory.create2<String, String>())
      allSessionsLengthStatement.binder.bind(InstantUtils.formatForDatabase(from), InstantUtils.formatForDatabase(until))
      val res = allSessionsLengthStatement.executeQuery()
      val map = mutableMapOf<RunConfigurationEventKind, Int>()
      while (res.next()) {
        val keyString = res.getString(0)
        val valueInt = res.getInt(1)
        if (keyString == null || valueInt == 0) continue

        val k = RunConfigurationEventKind.fromString(keyString) ?: continue

        map[k] = valueInt
      }

      map
    }
  }
}

enum class RunConfigurationEventKind(val eventName: String) {
  Run("run"), Debug("debug"), Build("build");

  companion object {
    fun fromString(eventName: String) = entries.find { it.eventName == eventName }
  }
}

internal class RunConfigurationListener : ExecutionListener {
  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    val id = System.identityHashCode(handler) // not the best ID out there, but it works

    when {
      env.runProfile.name.let { it.contains("[build", true) || it.startsWith("build ", true) } -> {
        FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(RunConfigurationTimeSpanUserActivity) {
          it.writeRunConfigurationStart(RunConfigurationEventKind.Build, id)
        }
      }
      env.executor is DefaultDebugExecutor || env.executor.id.contains("debug", true) -> {
        FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(RunConfigurationTimeSpanUserActivity) {
          it.writeRunConfigurationStart(RunConfigurationEventKind.Debug, id)
        }
      }
      else -> {
        FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(RunConfigurationTimeSpanUserActivity) {
          it.writeRunConfigurationStart(RunConfigurationEventKind.Run, id)
        }
      }
    }
  }

  override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
    val id = System.identityHashCode(handler)
    FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(RunConfigurationTimeSpanUserActivity) {
      it.writeEnd(id)
    }
  }
}

internal class BuildListenerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<BuildViewManager>().addListener(
      BuildListener(),
      FeatureUsageDatabaseCountersScopeProvider.getDisposable(project)
    )
  }
}

internal class BuildListener : BuildProgressListener {
  override fun onEvent(buildId: Any, event: BuildEvent) {
    val id = System.identityHashCode(buildId)
    when (event) {
      is StartBuildEvent -> {
        // Skip Gradle initial loading
        if (event.buildDescriptor.title == "Classes up-to-date check") return
        FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(RunConfigurationTimeSpanUserActivity) {
          it.writeRunConfigurationStart(RunConfigurationEventKind.Build, id)
        }
      }
      is FinishBuildEvent -> {
        FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(RunConfigurationTimeSpanUserActivity) {
          it.writeEnd(id)
        }
      }
    }
  }
}