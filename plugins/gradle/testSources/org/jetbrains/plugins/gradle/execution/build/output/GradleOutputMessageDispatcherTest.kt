// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.FinishEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.build.events.StartEvent
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gradle.execution.build.output.GradleOutputDispatcherFactory.GradleOutputMessageDispatcher
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer

@TestApplication
class GradleOutputMessageDispatcherTest {

  @Test
  fun `test task output is routed to task reader with correct parentEventId`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.onEvent(buildId, startEvent)
    dispatcher.appendLine("> Task :task")
    dispatcher.appendLine("task output")
    dispatcher.onEvent(buildId, finishEvent)
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    parser.assertLines(
      startEvent.id to listOf("> Task :task", "task output", "", "")
    )
    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) },
      buildId to { assertEqualsOrdered(listOf(startEvent, finishEvent), it) },
      startEvent.id to { assertOutputEventsOrdered(listOf("> Task :task\n", "task output\n", "\n"), it) }
    )
  }

  @Test
  fun `test two tasks output is routed to their own readers`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent1 = StartEvent.builder(Any(), ":task1").withParentId(buildId).build()
    val startEvent2 = StartEvent.builder(Any(), ":task2").withParentId(buildId).build()
    val finishEvent1 = FinishEvent.builder(Any(), ":task1", SuccessResultImpl()).withParentId(buildId).build()
    val finishEvent2 = FinishEvent.builder(Any(), ":task2", SuccessResultImpl()).withParentId(buildId).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.onEvent(buildId, startEvent1)
    dispatcher.onEvent(buildId, startEvent2)
    dispatcher.appendLine("> Task :task1")
    dispatcher.appendLine("task1 output")
    dispatcher.appendLine("> Task :task2")
    dispatcher.appendLine("task2 output")
    dispatcher.onEvent(buildId, finishEvent1)
    dispatcher.onEvent(buildId, finishEvent2)
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    parser.assertLines(
      startEvent1.id to listOf("> Task :task1", "task1 output", ""),
      startEvent2.id to listOf("> Task :task2", "task2 output", "", ""),
    )
    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) },
      buildId to { assertEqualsOrdered(listOf(startEvent1, startEvent2, finishEvent1, finishEvent2), it) },
      startEvent1.id to { assertOutputEventsOrdered(listOf("> Task :task1\n", "task1 output\n"), it) },
      startEvent2.id to { assertOutputEventsOrdered(listOf("> Task :task2\n", "task2 output\n", "\n"), it) }
    )
  }

  @Test
  fun `test output after task finish event is not lost`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.onEvent(buildId, startEvent)
    dispatcher.appendLine("> Task :task")
    dispatcher.onEvent(buildId, finishEvent)
    dispatcher.appendLine("output after finish event")
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    parser.assertLines(
      startEvent.id to listOf("> Task :task", "output after finish event", "", "")
    )
    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) },
      buildId to { assertEqualsOrdered(listOf(startEvent, finishEvent), it) },
      startEvent.id to { assertOutputEventsOrdered(listOf("> Task :task\n", "output after finish event\n", "\n"), it) }
    )
  }

  @Test
  fun `test up to date task with no output closes cleanly`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.onEvent(buildId, startEvent)
    dispatcher.onEvent(buildId, finishEvent)
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    parser.assertLines()
    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) },
      buildId to { assertEqualsOrdered(listOf(startEvent, finishEvent), it) }
    )
  }

  @Test
  fun `test task re-invocation both outputs parsed under task name`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent1 = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent1 = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()
    val startEvent2 = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent2 = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.onEvent(buildId, startEvent1)
    dispatcher.appendLine("> Task :task")
    dispatcher.appendLine("first run")
    dispatcher.onEvent(buildId, finishEvent1)
    dispatcher.onEvent(buildId, startEvent2)
    dispatcher.appendLine("> Task :task")
    dispatcher.appendLine("second run")
    dispatcher.onEvent(buildId, finishEvent2)
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    parser.assertLines(
      startEvent1.id to listOf("> Task :task", "first run", ""),
      startEvent2.id to listOf("> Task :task", "second run", "", ""),
    )
    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) },
      buildId to { assertEqualsOrdered(listOf(startEvent1, finishEvent1, startEvent2, finishEvent2), it) },
      startEvent1.id to { assertOutputEventsOrdered(listOf("> Task :task\n", "first run\n"), it) },
      startEvent2.id to { assertOutputEventsOrdered(listOf("> Task :task\n", "second run\n", "\n"), it) }
    )
  }

  @Test
  fun `test output before any start event falls back to root reader`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.appendLine("root output")
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    parser.assertLines(
      buildId to listOf("root output", "", "")
    )
    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) }
    )
  }

  @Test
  fun `test output after build successful goes to root reader`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.onEvent(buildId, startEvent)
    dispatcher.appendLine("> Task :task")
    dispatcher.appendLine("task output")
    dispatcher.onEvent(buildId, finishEvent)
    dispatcher.appendLine("BUILD SUCCESSFUL in 1s")
    dispatcher.appendLine("2 actionable tasks: 2 executed")
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    parser.assertLines(
      startEvent.id to listOf("> Task :task", "task output", ""),
      buildId to listOf("BUILD SUCCESSFUL in 1s", "2 actionable tasks: 2 executed", "", ""),
    )
    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) },
      buildId to { assertEqualsOrdered(listOf(startEvent, finishEvent), it) },
      startEvent.id to { assertOutputEventsOrdered(listOf("> Task :task\n", "task output\n"), it) }
    )
  }

  @Test
  fun `test parser-generated events are re-parented to task event id`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = BuildOutputParser { _, reader, messageConsumer ->
      reader.readAllLines()
      messageConsumer.accept(
        MessageEvent.builder("message", MessageEvent.Kind.ERROR)
          .withParentId(reader.parentEventId)
          .build()
      )
      true
    }

    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.onEvent(buildId, startEvent)
    dispatcher.appendLine("> Task :task")
    dispatcher.appendLine("task output")
    dispatcher.onEvent(buildId, finishEvent)
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) },
      buildId to { assertEqualsOrdered(listOf(startEvent, finishEvent), it) },
      startEvent.id to { assertEventsOrdered(listOf("O:> Task :task\n", "O:task output\n", "O:\n", "M:message"), it) },
    )
  }

  @Test
  fun `test output after configure line goes to root reader`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("", DefaultBuildDescriptor(buildId, "", "", 0L)).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "", SuccessResultImpl()).build()
    val startEvent = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    val dispatcher = GradleOutputMessageDispatcher(buildId, listener, false, listOf(parser))
    dispatcher.onEvent(buildId, startBuildEvent)
    dispatcher.onEvent(buildId, startEvent)
    dispatcher.appendLine("> Task :task")
    dispatcher.appendLine("task output")
    dispatcher.onEvent(buildId, finishEvent)
    dispatcher.appendLine("> Configure project :project")
    dispatcher.appendLine("configure output")
    dispatcher.onEvent(buildId, finishBuildEvent)
    dispatcher.closeBlocking()

    parser.assertLines(
      startEvent.id to listOf("> Task :task", "task output", ""),
      buildId to listOf("> Configure project :project", "configure output", "", ""),
    )
    listener.assertEvents(
      null to { assertEqualsOrdered(listOf(startBuildEvent, finishBuildEvent), it) },
      buildId to { assertEqualsOrdered(listOf(startEvent, finishEvent), it) },
      startEvent.id to { assertOutputEventsOrdered(listOf("> Task :task\n", "task output\n"), it) }
    )
  }

  private fun GradleOutputMessageDispatcher.closeBlocking() {
    val latch = CountDownLatch(1)
    invokeOnCompletion { latch.countDown() }
    close()
    latch.await()
  }

  private class RecordingBuildOutputParser : BuildOutputParser {

    private val actual = CopyOnWriteArrayList<Pair<Any, List<String>>>()

    fun assertLines(vararg expected: Pair<Any, List<String>>) {
      assertEqualsUnordered(expected.asList(), actual)
    }

    override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
      val lines = listOf(line) + reader.readAllLines()
      actual.add(reader.parentEventId to lines)
      return true
    }
  }

  private class RecordingBuildProgressListener : BuildProgressListener {

    private val actual = ConcurrentHashMap<Optional<Any>, CopyOnWriteArrayList<BuildEvent>>()

    fun assertEvents(vararg expected: Pair<Any?, (List<BuildEvent>) -> Unit>) {
      assertEqualsUnordered(expected.map { Optional.ofNullable(it.first) }, actual.keys)
      for ((key, assertValue) in expected) {
        assertValue(actual.getValue(Optional.ofNullable(key)))
      }
    }

    override fun onEvent(buildId: Any, event: BuildEvent) {
      actual.computeIfAbsent(Optional.ofNullable(event.parentId)) { CopyOnWriteArrayList() }
        .add(event)
    }
  }

  companion object {

    private fun assertOutputEventsOrdered(expected: List<String>, actual: Collection<BuildEvent>) {
      assertEventsOrdered(expected.map { "O:$it" }, actual)
    }

    private fun assertEventsOrdered(expected: List<String>, actual: Collection<BuildEvent>) {
      assertEqualsOrdered(expected, actual.map {
        when (it) {
          is OutputBuildEvent -> "O:" + it.message
          is MessageEvent -> "M:" + it.message
          else -> throw AssertionError("Unexpected event $it")
        }
      })
    }

    private fun BuildOutputInstantReader.readAllLines(): List<String> {
      val lines = ArrayList<String>()
      while (true) {
        val next = readLine() ?: break
        lines.add(next)
      }
      return lines
    }
  }
}
