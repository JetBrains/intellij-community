// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.FinishEvent
import com.intellij.build.events.StartEvent
import com.intellij.build.events.impl.OutputBuildEventImpl
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.LineProcessor
import com.intellij.openapi.externalSystem.service.execution.AbstractOutputMessageDispatcher
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputDispatcherFactory
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputMessageDispatcher
import org.apache.commons.lang3.ClassUtils
import org.gradle.api.logging.LogLevel
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class GradleOutputDispatcherFactory : ExternalSystemOutputDispatcherFactory {
  override val externalSystemId = GradleConstants.SYSTEM_ID

  override fun create(
    buildId: Any,
    buildProgressListener: BuildProgressListener,
    appendOutputToMainConsole: Boolean,
    parsers: List<BuildOutputParser>
  ): ExternalSystemOutputMessageDispatcher {
    return GradleOutputMessageDispatcher(buildId, buildProgressListener, appendOutputToMainConsole, parsers)
  }

  private class GradleOutputMessageDispatcher(
    private val buildId: Any,
    private val myBuildProgressListener: BuildProgressListener,
    private val appendOutputToMainConsole: Boolean,
    private val parsers: List<BuildOutputParser>
  ) : AbstractOutputMessageDispatcher(
    myBuildProgressListener) {
    override var stdOut: Boolean = true
    private val lineProcessor: LineProcessor
    private val myRootReader: BuildOutputInstantReaderImpl
    private val tasksOutputReaders: MutableMap<String, BuildOutputInstantReaderImpl> = ConcurrentHashMap()
    private val tasksEventIds: MutableMap<String, Any> = ConcurrentHashMap()
    private val redefinedReaders = mutableListOf<BuildOutputInstantReaderImpl>()

    init {
      val deferredRootEvents = mutableListOf<BuildEvent>()
      myRootReader = object : BuildOutputInstantReaderImpl(buildId, buildId, BuildProgressListener { _: Any, event: BuildEvent ->
        var buildEvent = event
        val parentId = buildEvent.parentId
        if (parentId != buildId && parentId is String) {
          val taskEventId = tasksEventIds[parentId]
          if (taskEventId != null) {
            buildEvent = BuildEventInvocationHandler.wrap(event, taskEventId)
          }
        }
        if (buildEvent is DuplicateMessageAware) {
          deferredRootEvents += buildEvent
        }
        else {
          myBuildProgressListener.onEvent(buildId, buildEvent)
        }
      }, parsers) {
        override fun closeAndGetFuture(): CompletableFuture<Unit> =
          super.closeAndGetFuture().whenComplete { _, _ -> deferredRootEvents.forEach { myBuildProgressListener.onEvent(buildId, it) } }
      }

      lineProcessor = object : LineProcessor() {
        private var myCurrentReader: BuildOutputInstantReaderImpl = myRootReader
        override fun process(line: String) {
          val cleanLine = removeLoggerPrefix(line)
          // skip Gradle test runner output
          if (cleanLine.startsWith("<ijLog>")) return

          if (cleanLine.startsWith("> Task :")) {
            val taskName = cleanLine.removePrefix("> Task ").substringBefore(' ')
            myCurrentReader = tasksOutputReaders[taskName] ?: myRootReader
          }
          else if (cleanLine.startsWith("> Configure") ||
                   cleanLine.startsWith("FAILURE: Build failed") ||
                   cleanLine.startsWith("FAILURE: Build completed") ||
                   cleanLine.startsWith("[Incubating] Problems report is available at:") ||
                   cleanLine.startsWith("CONFIGURE SUCCESSFUL") ||
                   cleanLine.startsWith("BUILD SUCCESSFUL")) {
            myCurrentReader = myRootReader
          }

          myCurrentReader.appendLine(cleanLine)
          if (myCurrentReader != myRootReader) {
            val parentEventId = myCurrentReader.parentEventId
            myBuildProgressListener.onEvent(buildId, OutputBuildEventImpl(parentEventId, line + '\n', stdOut)) //NON-NLS
          }
        }
      }
    }

    override fun onEvent(buildId: Any, event: BuildEvent) {
      super.onEvent(buildId, event)
      if (event.parentId != buildId) return
      if (event is StartEvent) {
        val eventId = event.id
        val oldValue = tasksOutputReaders.put(event.message,
                                              BuildOutputInstantReaderImpl(buildId, eventId, myBuildProgressListener, parsers))
        if (oldValue != null) {  // multiple invocations of the same task during the build session
          redefinedReaders.add(oldValue)
        }
        tasksEventIds[event.message] = eventId
      }
      else if (event is FinishEvent) {
        // unreceived output is still possible after finish task event but w/o long pauses between chunks
        // also no output expected for up-to-date tasks
        tasksOutputReaders[event.message]?.disableActiveReading()
      }
    }

    override fun closeAndGetFuture(): CompletableFuture<*> {
      lineProcessor.close()
      val futures = (tasksOutputReaders.values.asSequence()
                     + redefinedReaders.asSequence()
                     + sequenceOf(myRootReader))
        .map { it.closeAndGetFuture() }
        .toList()

      tasksOutputReaders.clear()
      redefinedReaders.clear()
      return CompletableFuture.allOf(*futures.toTypedArray())
    }

    override fun append(csq: CharSequence): Appendable {
      if (appendOutputToMainConsole) {
        myBuildProgressListener.onEvent(buildId, OutputBuildEventImpl(buildId, csq.toString(), stdOut)) //NON-NLS
      }
      lineProcessor.append(csq)
      return this
    }

    override fun append(csq: CharSequence, start: Int, end: Int): Appendable {
      if (appendOutputToMainConsole) {
        myBuildProgressListener.onEvent(buildId, OutputBuildEventImpl(buildId, csq.subSequence(start, end).toString(), stdOut)) //NON-NLS
      }
      lineProcessor.append(csq, start, end)
      return this
    }

    override fun append(c: Char): Appendable {
      if (appendOutputToMainConsole) {
        myBuildProgressListener.onEvent(buildId, OutputBuildEventImpl(buildId, c.toString(), stdOut))
      }
      lineProcessor.append(c)
      return this
    }

    private fun removeLoggerPrefix(line: String): String {
      val list = mutableListOf<String>()
      list += line.split(' ', limit = 3)
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
      return if (enumValues<LogLevel>().none { it.name == logLevel }) {
        line
      }
      else {
        line.drop(list.sumOf { it.length } + 2).trimStart()
      }
    }

    private class BuildEventInvocationHandler(
      private val buildEvent: BuildEvent,
      private val parentEventId: Any
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
  }
}

