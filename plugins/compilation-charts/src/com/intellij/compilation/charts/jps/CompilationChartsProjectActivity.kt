// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.jps

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.compilation.charts.CompilationChartsBundle
import com.intellij.compilation.charts.CompilationChartsFactory
import com.intellij.compilation.charts.CompilationChartsFactory.EventLayout
import com.intellij.compilation.charts.events.CpuStatisticChartEvent
import com.intellij.compilation.charts.events.MemoryStatisticChartEvent
import com.intellij.compilation.charts.events.ModuleFinishChartEvent
import com.intellij.compilation.charts.events.ModuleStartChartEvent
import com.intellij.compilation.charts.ui.Colors
import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.messages.MessageBusConnection
import java.util.ArrayDeque
import java.util.Queue
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class CompilationChartsProjectActivity : ProjectActivity {
  companion object {
    private val LOG: Logger = Logger.getInstance(CompilationChartsProjectActivity::class.java)
    const val COMPILATION_CHARTS_KEY: String = "compilation.charts"
    const val COMPILATION_STATISTIC_BUILDER_ID: String = "jps.compile.statistic"
    const val COMPILATION_STATUS_BUILDER_ID: String = "jps.compile.status"
  }

  override suspend fun execute(project: Project) {
    if (!Registry.`is`(COMPILATION_CHARTS_KEY)) return

    val connection: MessageBusConnection = project.messageBus.connect()
    val handler = CompilationChartsMessageHandler()
    connection.subscribe(CustomBuilderMessageHandler.TOPIC, handler)

    val view = project.getService(BuildViewManager::class.java)
    val disposable = Disposer.newDisposable(view, "Compilation charts event listener disposable")
    view.addListener(CompilationChartsBuildProgressListener(project, handler, view, disposable), disposable)
  }

  private class CompilationChartsBuildProgressListener(
    private val project: Project,
    private val handler: CompilationChartsMessageHandler,
    private val view: BuildViewManager,
    private val disposable: Disposable,
  ) : BuildProgressListener {
    override fun onEvent(buildId: Any, event: BuildEvent) {
      when (event) {
        is StartBuildEvent -> {
          val title = event.buildDescriptor.title.lowercase()
          if (title.contains("up-to-date") || title.startsWith("worksheet")) return

          val chart = CompilationChartsFactory.getInstance()
            .registerEvent(MemoryStatisticChartEvent::class.java,
                           CompilationChartsBundle.message("charts.memory.type"),
                           CompilationChartsFactory.EventColor(Colors.Memory.BACKGROUND, Colors.Memory.BORDER),
                           object : EventLayout {
                             override fun apply(time: Long, data: Long): String = "${data / (1024 * 1024)} " + CompilationChartsBundle.message("charts.memory.unit")
                           })
            .registerEvent(CpuStatisticChartEvent::class.java,
                           CompilationChartsBundle.message("charts.cpu.type"),
                           CompilationChartsFactory.EventColor(Colors.Cpu.BACKGROUND, Colors.Cpu.BORDER),
                           object : EventLayout {
                             override fun apply(time: Long, data: Long): String = "$data " + CompilationChartsBundle.message("charts.cpu.unit")
                           })
            .create(project, disposable)
          handler.addState(CompilationChartsBuildEvent(view, buildId, chart))
        }
        is FinishBuildEvent -> {
          val title = event.message.lowercase()
          if (title.contains("up-to-date") || title.startsWith("worksheet")) return

          handler.removeState()
        }
      }
    }
  }

  private class CompilationChartsMessageHandler : CustomBuilderMessageHandler {
    private val json = ObjectMapper(JsonFactory()).registerModule(KotlinModule.Builder().build())
    private val states: Queue<CompilationChartsBuildEvent> = ArrayDeque()
    private var currentState: CompilationChartsBuildEvent? = null
    private val defaultUUID: UUID = UUID.randomUUID()
    private val lateInitState = AtomicReference<(CompilationChartsBuildEvent) -> Unit>()

    fun addState(chart: CompilationChartsBuildEvent) {
      states.add(chart)
      if (currentState == null) removeState()
    }

    fun removeState() {
      currentState = states.poll()
    }

    override fun messageReceived(builderId: String?, messageType: String?, messageText: String?) {
      messageReceived(defaultUUID, builderId, messageType, messageText)
    }

    override fun messageReceived(sessionId: UUID, builderId: String?, messageType: String?, messageText: String?) {
      when (builderId) {
        COMPILATION_STATUS_BUILDER_ID -> status(messageType)
        COMPILATION_STATISTIC_BUILDER_ID -> statistic(messageType, messageText)
      }
    }

    private fun status(messageType: String?) {
      when (messageType) {
        "START" -> {
          lateInitState.set { state -> state.view.onEvent(state.buildId, state) }
        }
        "FINISH" -> {}
      }
    }

    fun statistic(messageType: String?, messageText: String?) {
      try {
        when (messageType) {
          "STARTED" -> currentState?.also { state ->
            lateInitState.getAndSet(null)?.invoke(state)
            val values = json.readValue(messageText, object : TypeReference<List<StartTarget>>() {})
            val events = values.map { value -> ModuleStartChartEvent(value.name, value.type, value.isTest, value.isFileBased, value.time, value.thread) }
            state.chart.putAll(events)
          }
          "FINISHED" -> currentState?.also { state ->
            val values = json.readValue(messageText, object : TypeReference<List<FinishTarget>>() {})
            val events = values.map { value -> ModuleFinishChartEvent(value.name, value.type, value.isTest, value.isFileBased, value.time, value.thread) }
            state.chart.putAll(events)
          }
          "STATISTIC" -> currentState?.also { state ->
            val event = json.readValue(messageText, CpuMemoryStatistics::class.java)
            state.chart.put(MemoryStatisticChartEvent(event.time, event.heapUsed.toDouble(), event.heapMax.toDouble()))
            state.chart.put(CpuStatisticChartEvent(event.time, event.cpu.toDouble(), 100.0))
          }
        }
      }
      catch (e: JsonProcessingException) {
        LOG.warn("Failed to parse message: $messageText", e)
      }
    }
  }
}