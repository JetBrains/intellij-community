// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildId
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.OutputId
import com.intellij.build.events.OutputReferenceEvent
import com.intellij.build.events.StartEvent
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputLineProcessor
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.BuildOutputParserDispatcher
import com.intellij.build.output.BuildOutputText
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemOutputMessageDispatcher
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputDispatcherFactory
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputMessageDispatcher
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.ClassUtils
import org.gradle.api.logging.LogLevel
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.enums.enumEntries

@Internal
class GradleOutputDispatcherFactory : ExternalSystemOutputDispatcherFactory {

  override val externalSystemId: ProjectSystemId = GradleConstants.SYSTEM_ID

  override fun create(
    buildId: BuildId,
    buildProgressListener: BuildProgressListener,
    parsers: List<BuildOutputParser>,
    consoleSettings: BuildViewSettingsProvider,
  ): ExternalSystemOutputMessageDispatcher {
    return GradleOutputMessageDispatcher(buildId, buildProgressListener, parsers, consoleSettings)
  }

  @Internal
  data class TaskNameId(val name: String)

  @VisibleForTesting
  class GradleOutputMessageDispatcher(
    buildId: BuildId,
    listener: BuildProgressListener,
    parsers: List<BuildOutputParser>,
    consoleSettings: BuildViewSettingsProvider,
  ) : AbstractExternalSystemOutputMessageDispatcher(buildId, listener, parsers, consoleSettings) {

    private val tasksEventIds: MutableMap<TaskNameId, StartId> = ConcurrentHashMap()
    private val deferredTaskEvents: MutableMap<TaskNameId, MutableList<BuildEvent>> = ConcurrentHashMap()
    private val lineProcessors: MutableMap<ProcessOutputType, GradleOutputLineProcessor> = ConcurrentHashMap()

    private var currentTaskId: StartId = buildId

    private inner class GradleOutputLineProcessor(
      private val outputType: ProcessOutputType,
    ) {

      private val parserDispatchers: MutableMap<StartId, BuildOutputParserDispatcher> = ConcurrentHashMap()

      private val delegate = BuildOutputLineProcessor { line ->
        val cleanLine = removeLoggerPrefix(line)
        // skip Gradle test runner output
        if (cleanLine.text.startsWith("<ijLog>")) return@BuildOutputLineProcessor

        if (cleanLine.text.startsWith("> Task :")) {
          val taskNameId = TaskNameId(
            cleanLine.text
              .removePrefix("> Task ")
              .trim()
              .substringBefore(' ')
          )
          // multiple invocations of the same task during the build session should flush collected messages
          parserDispatchers.remove(taskNameId)?.close()
          currentTaskId = taskNameId
        }
        else if (cleanLine.text.startsWith("> Configure") ||
                 cleanLine.text.startsWith("FAILURE: Build failed") ||
                 cleanLine.text.startsWith("FAILURE: Build completed") ||
                 cleanLine.text.startsWith("[Incubating] Problems report is available at:") ||
                 cleanLine.text.startsWith("CONFIGURE SUCCESSFUL") ||
                 cleanLine.text.startsWith("BUILD SUCCESSFUL")) {
          currentTaskId = buildId
        }

        val taskId = currentTaskId

        if (taskId != buildId) {
          val outputEvent = when (consoleSettings.isSingleBuildConsoleView) {
            true ->
              OutputReferenceEvent.builder(taskId, line.ids)
                .build()
            else ->
              OutputBuildEvent.builder(line.text)
                .withParentId(taskId)
                .withOutputType(outputType)
                .build()
          }
          notifyBuildEvent(buildId, outputEvent)
        }

        parserDispatchers.getOrPut(taskId) { createParserDispatcher(taskId) }
          .notifyTextAvailable(cleanLine)
      }

      suspend fun notifyTextAvailable(text: BuildOutputText) {
        delegate.notifyTextAvailable(text)
      }

      suspend fun close() {
        delegate.close()

        for (parserDispatcher in parserDispatchers.values) {
          parserDispatcher.close()
        }
      }
    }

    override fun notifyBuildEvent(buildId: BuildId, event: BuildEvent) {
      val buildEvent = when (val parentId = event.parentId) {
        buildId -> event
        is TaskNameId -> {
          val taskEventId = tasksEventIds[parentId] ?: run {
            deferredTaskEvents.getOrPut(parentId) { ArrayList() }
              .add(event)
            return
          }
          BuildEventInvocationHandler.wrap(event, taskEventId)
        }
        is String -> {
          val taskEventId = tasksEventIds[TaskNameId(parentId)]
          if (taskEventId != null) {
            LOG.error(
              "The implicit API for the event redefinition via String parent event ID is deprecated. Use TaskNameId instead.",
              Throwable()
            )
          }
          when (taskEventId != null) {
            true -> BuildEventInvocationHandler.wrap(event, taskEventId)
            else -> event
          }
        }
        else -> event
      }

      super.notifyBuildEvent(buildId, buildEvent)

      if (event.parentId == buildId && event is StartEvent) {
        val taskEventId = event.id
        val taskNameId = TaskNameId(event.message)
        tasksEventIds[taskNameId] = taskEventId

        val deferredEvents = deferredTaskEvents.remove(taskNameId) ?: emptyList()
        for (deferredEvent in deferredEvents) {
          notifyBuildEvent(buildId, deferredEvent)
        }
      }
    }

    override fun doClose() {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking {
        for (lineProcessor in lineProcessors.values) {
          lineProcessor.close()
        }
      }
    }

    override fun notifyTextAvailable(text: String, outputType: ProcessOutputType) {
      val outputId = OutputId()

      notifyBuildEvent(
        buildId,
        OutputBuildEvent.builder(text) //NON-NLS
          .withId(outputId)
          .withOutputType(outputType)
          .build()
      )

      @Suppress("RAW_RUN_BLOCKING")
      runBlocking {
        lineProcessors.getOrPut(outputType) { GradleOutputLineProcessor(outputType) }
          .notifyTextAvailable(BuildOutputText.createOutputText(outputId, text))
      }
    }

    private fun removeLoggerPrefix(line: BuildOutputText): BuildOutputText {
      val list = mutableListOf<String>()
      list += line.text.split(' ', limit = 3)
      if (list.size < 3) return line
      if (!list[1].startsWith('[') || !list[1].endsWith(']')) return line
      if (!list[2].startsWith('[')) return line
      if (!list[2].endsWith(']')) {
        val i = list[2].indexOf(']')
        if (i == -1) return line
        list[2] = list[2].substring(0, i + 1)
        if (!list[2].endsWith(']')) return line
      }

      val logLevel = list[1].drop(1).dropLast(1)
      return if (enumEntries<LogLevel>().none { it.name == logLevel }) {
        line
      }
      else {
        BuildOutputText.createOutputText(
          line.ids,
          line.text.drop(list.sumOf { it.length } + 2).trimStart()
        )
      }
    }

    private class BuildEventInvocationHandler(
      private val buildEvent: BuildEvent,
      private val parentEventId: Any,
    ) : InvocationHandler {
      override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
        if (method?.name.equals("getParentId")) return parentEventId
        return method?.invoke(buildEvent, *args ?: arrayOfNulls<Any>(0))
      }

      companion object {
        fun wrap(buildEvent: BuildEvent, parentEventId: Any): BuildEvent {
          val classLoader = buildEvent.javaClass.classLoader
          val interfaces = ClassUtils.getAllInterfaces(buildEvent.javaClass)
            .filterIsInstance(Class::class.java)
            .toTypedArray()
          val invocationHandler = BuildEventInvocationHandler(buildEvent, parentEventId)
          return Proxy.newProxyInstance(classLoader, interfaces, invocationHandler) as BuildEvent
        }
      }
    }

    companion object {
      private val LOG = logger<GradleOutputMessageDispatcher>()
    }
  }
}

