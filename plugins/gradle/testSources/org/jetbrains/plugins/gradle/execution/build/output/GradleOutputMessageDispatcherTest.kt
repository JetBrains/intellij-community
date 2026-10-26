// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildId
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.FinishEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.OutputReferenceEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.build.events.StartEvent
import com.intellij.build.events.StartId
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.build.output.BuildOutputMulticaster
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.BuildOutputReplayableLineReader
import com.intellij.build.output.BuildOutputText
import com.intellij.execution.process.ProcessOutputType
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gradle.execution.build.output.GradleOutputDispatcherFactory.GradleOutputMessageDispatcher
import org.jetbrains.plugins.gradle.execution.build.output.GradleOutputDispatcherFactory.TaskNameId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

@TestApplication
@ParameterizedClass
@ValueSource(booleans = [true, false])
class GradleOutputMessageDispatcherTest(private val useSingleBuildConsole: Boolean) {

  private class TestSettingsProvider(val useSingleBuildConsole: Boolean) : BuildViewSettingsProvider {
    override fun isSingleBuildConsoleView(): Boolean = useSingleBuildConsole
  }

  @Test
  fun `test task output is routed to task reader with correct parentEventId`() {
    val buildId = BuildId()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent = StartEvent.builder(StartId(), ":task").withParentId(startBuildEvent.id).build()
    val finishEvent = FinishEvent.builder(startEvent.id, ":task", SuccessResultImpl()).withParentId(startBuildEvent.id).build()
    val finishBuildEvent = FinishBuildEvent.builder(startBuildEvent.id, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole))
      .use { dispatcher ->
        dispatcher.notifyBuildEvent(buildId, startBuildEvent)
        dispatcher.notifyBuildEvent(buildId, startEvent)
        dispatcher.notifyTextAvailable("> Task :task\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("task output\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishEvent)
        dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
      }

    parser.assertLines(
      TaskNameId(":task") to listOf("> Task :task", "task output")
    )
    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("> Task :task\n")
          outputEvent("task output\n")
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent)
          event(finishEvent)
        }
        node(startEvent.id) {
          taskOutput("> Task :task\n")
          taskOutput("task output\n")
        }
      },
      listener.events
    )
  }

  @Test
  fun `test two tasks output is routed to their own readers`() {
    val buildId = BuildId()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent1 = StartEvent.builder(StartId(), ":task1").withParentId(startBuildEvent.id).build()
    val startEvent2 = StartEvent.builder(StartId(), ":task2").withParentId(startBuildEvent.id).build()
    val finishEvent1 = FinishEvent.builder(startEvent1.id, ":task1", SuccessResultImpl()).withParentId(startBuildEvent.id).build()
    val finishEvent2 = FinishEvent.builder(startEvent2.id, ":task2", SuccessResultImpl()).withParentId(startBuildEvent.id).build()
    val finishBuildEvent = FinishBuildEvent.builder(startBuildEvent.id, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole))
      .use { dispatcher ->
        dispatcher.notifyBuildEvent(buildId, startBuildEvent)
        dispatcher.notifyBuildEvent(buildId, startEvent1)
        dispatcher.notifyBuildEvent(buildId, startEvent2)
        dispatcher.notifyTextAvailable("> Task :task1\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("task1 output\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("> Task :task2\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("task2 output\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishEvent1)
        dispatcher.notifyBuildEvent(buildId, finishEvent2)
        dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
      }

    parser.assertLines(
      TaskNameId(":task1") to listOf("> Task :task1", "task1 output"),
      TaskNameId(":task2") to listOf("> Task :task2", "task2 output"),
    )
    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("> Task :task1\n")
          outputEvent("task1 output\n")
          outputEvent("> Task :task2\n")
          outputEvent("task2 output\n")
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent1)
          event(startEvent2)
          event(finishEvent1)
          event(finishEvent2)
        }
        node(startEvent1.id) {
          taskOutput("> Task :task1\n")
          taskOutput("task1 output\n")
        }
        node(startEvent2.id) {
          taskOutput("> Task :task2\n")
          taskOutput("task2 output\n")
        }
      },
      listener.events
    )
  }

  @Test
  fun `test output after task finish event is not lost`() {
    val buildId = BuildId()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent = StartEvent.builder(StartId(), ":task").withParentId(startBuildEvent.id).build()
    val finishEvent = FinishEvent.builder(startEvent.id, ":task", SuccessResultImpl()).withParentId(startBuildEvent.id).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole))
      .use { dispatcher ->
        dispatcher.notifyBuildEvent(buildId, startBuildEvent)
        dispatcher.notifyBuildEvent(buildId, startEvent)
        dispatcher.notifyTextAvailable("> Task :task\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishEvent)
        dispatcher.notifyTextAvailable("output after finish event\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
      }

    parser.assertLines(
      TaskNameId(":task") to listOf("> Task :task", "output after finish event")
    )
    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("> Task :task\n")
          outputEvent("output after finish event\n")
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent)
          event(finishEvent)
        }
        node(startEvent.id) {
          taskOutput("> Task :task\n")
          taskOutput("output after finish event\n")
        }
      },
      listener.events
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
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole))
      .use { dispatcher ->
        dispatcher.notifyBuildEvent(buildId, startBuildEvent)
        dispatcher.notifyBuildEvent(buildId, startEvent)
        dispatcher.notifyBuildEvent(buildId, finishEvent)
        dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
      }

    parser.assertLines()
    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent)
          event(finishEvent)
        }
      },
      listener.events
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
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole))
      .use { dispatcher ->
        dispatcher.notifyBuildEvent(buildId, startBuildEvent)
        dispatcher.notifyBuildEvent(buildId, startEvent1)
        dispatcher.notifyTextAvailable("> Task :task\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("first run\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishEvent1)
        dispatcher.notifyBuildEvent(buildId, startEvent2)
        dispatcher.notifyTextAvailable("> Task :task\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("second run\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishEvent2)
        dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
      }

    parser.assertLines(
      TaskNameId(":task") to listOf("> Task :task", "first run"),
      TaskNameId(":task") to listOf("> Task :task", "second run"),
    )
    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("> Task :task\n")
          outputEvent("first run\n")
          outputEvent("> Task :task\n")
          outputEvent("second run\n")
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent1)
          event(finishEvent1)
          event(startEvent2)
          event(finishEvent2)
        }
        node(startEvent1.id) {
          taskOutput("> Task :task\n")
          taskOutput("first run\n")
        }
        node(startEvent2.id) {
          taskOutput("> Task :task\n")
          taskOutput("second run\n")
        }
      },
      listener.events
    )
  }

  @Test
  fun `test output before any start event falls back to root reader`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val parser = RecordingBuildOutputParser()
    val listener = RecordingBuildProgressListener()
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole))
      .use { dispatcher ->
        dispatcher.notifyBuildEvent(buildId, startBuildEvent)
        dispatcher.notifyTextAvailable("root output\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
      }

    parser.assertLines(
      buildId to listOf("root output")
    )
    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("root output\n")
          event(finishBuildEvent)
        }
      },
      listener.events
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
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole))
      .use { dispatcher ->
        dispatcher.notifyBuildEvent(buildId, startBuildEvent)
        dispatcher.notifyBuildEvent(buildId, startEvent)
        dispatcher.notifyTextAvailable("> Task :task\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("task output\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishEvent)
        dispatcher.notifyTextAvailable("BUILD SUCCESSFUL in 1s\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("2 actionable tasks: 2 executed\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
      }

    parser.assertLines(
      TaskNameId(":task") to listOf("> Task :task", "task output"),
      buildId to listOf("BUILD SUCCESSFUL in 1s", "2 actionable tasks: 2 executed"),
    )
    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("> Task :task\n")
          outputEvent("task output\n")
          outputEvent("BUILD SUCCESSFUL in 1s\n")
          outputEvent("2 actionable tasks: 2 executed\n")
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent)
          event(finishEvent)
        }
        node(startEvent.id) {
          taskOutput("> Task :task\n")
          taskOutput("task output\n")
        }
      },
      listener.events
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
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole)).use { dispatcher ->
      dispatcher.notifyBuildEvent(buildId, startBuildEvent)
      dispatcher.notifyBuildEvent(buildId, startEvent)
      dispatcher.notifyTextAvailable("> Task :task\n", ProcessOutputType.STDOUT)
      dispatcher.notifyTextAvailable("task output\n", ProcessOutputType.STDOUT)
      dispatcher.notifyBuildEvent(buildId, finishEvent)
      dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
    }

    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("> Task :task\n")
          outputEvent("task output\n")
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent)
          event(finishEvent)
        }
        node(startEvent.id) {
          taskOutput("> Task :task\n")
          taskOutput("task output\n")
          messageEvent(MessageEvent.Kind.ERROR, "message")
        }
      },
      listener.events
    )
  }

  @Test
  fun `test output arrives before start event has correct parentEventId`() {
    val buildId = Any()
    val startBuildEvent = StartBuildEvent.builder("Build", DefaultBuildDescriptor(buildId, "Build", "/", 0L)).build()
    val startEvent = StartEvent.builder(Any(), ":task").withParentId(buildId).build()
    val finishEvent = FinishEvent.builder(Any(), ":task", SuccessResultImpl()).withParentId(buildId).build()
    val finishBuildEvent = FinishBuildEvent.builder(buildId, "Build", SuccessResultImpl()).build()

    val listener = RecordingBuildProgressListener()
    GradleOutputMessageDispatcher(buildId, listener, emptyList(), TestSettingsProvider(useSingleBuildConsole)).use { dispatcher ->
      dispatcher.notifyBuildEvent(buildId, startBuildEvent)
      dispatcher.notifyTextAvailable("> Task :task\n", ProcessOutputType.STDOUT)
      dispatcher.notifyTextAvailable("task output\n", ProcessOutputType.STDOUT)
      dispatcher.notifyBuildEvent(buildId, startEvent)
      dispatcher.notifyBuildEvent(buildId, finishEvent)
      dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
    }

    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("> Task :task\n")
          outputEvent("task output\n")
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent)
          event(finishEvent)
        }
        node(startEvent.id) {
          taskOutput("> Task :task\n")
          taskOutput("task output\n")
        }
      },
      listener.events
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
    GradleOutputMessageDispatcher(buildId, listener, listOf(parser), TestSettingsProvider(useSingleBuildConsole))
      .use { dispatcher ->
        dispatcher.notifyBuildEvent(buildId, startBuildEvent)
        dispatcher.notifyBuildEvent(buildId, startEvent)
        dispatcher.notifyTextAvailable("> Task :task\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("task output\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishEvent)
        dispatcher.notifyTextAvailable("> Configure project :project\n", ProcessOutputType.STDOUT)
        dispatcher.notifyTextAvailable("configure output\n", ProcessOutputType.STDOUT)
        dispatcher.notifyBuildEvent(buildId, finishBuildEvent)
      }

    parser.assertLines(
      TaskNameId(":task") to listOf("> Task :task", "task output"),
      buildId to listOf("> Configure project :project", "configure output"),
    )
    assertEvents(
      expectedEvents {
        root {
          event(startBuildEvent)
          outputEvent("> Task :task\n")
          outputEvent("task output\n")
          outputEvent("> Configure project :project\n")
          outputEvent("configure output\n")
          event(finishBuildEvent)
        }
        node(buildId) {
          event(startEvent)
          event(finishEvent)
        }
        node(startEvent.id) {
          taskOutput("> Task :task\n")
          taskOutput("task output\n")
        }
      },
      listener.events
    )
  }

  sealed class TestBuildOutputParser : BuildOutputParser {

    lateinit var beforeReadCallback: () -> Unit
    lateinit var afterReadCallback: () -> Unit

    fun onParserLifecycleEvent(beforeRead: () -> Unit, afterRead: () -> Unit) {
      this.beforeReadCallback = beforeRead
      this.afterReadCallback = afterRead
    }

    override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean =
      getDelegate().parse(line, reader, messageConsumer)

    abstract fun getDelegate(): BuildOutputParser
  }

  class SuspendableParser : TestBuildOutputParser() {

    override fun toString(): String  = this.javaClass.simpleName

    override fun getDelegate(): BuildOutputParser = object : BuildOutputLineParserBridge() {
      override suspend fun parse(
        line: BuildOutputText,
        reader: BuildOutputReplayableLineReader,
        taskId: StartId,
        multicaster: BuildOutputMulticaster,
        isSingleFlow: Boolean,
      ): Boolean {
        beforeReadCallback()
        while (reader.readLine() != null) continue // suspends until EOO (End of Output)
        afterReadCallback()
        return true
      }
    }
  }

  class BlockingParser : TestBuildOutputParser() {

    override fun toString(): String  = this.javaClass.simpleName

    override fun getDelegate(): BuildOutputParser = BuildOutputParser { _, reader, _ ->
      beforeReadCallback()
      reader.readAllLines() // hangs until EOO (End of Output)
      afterReadCallback()
      true
    }
  }

  @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  @ParameterizedTest(name = "{0} builds x {1} tasks with {2}")
  @MethodSource("hangingParsersArguments")
  fun `test hanging parsers do not exhaust the parser dispatcher`(numBuilds: Int, numTasks: Int, parser: TestBuildOutputParser) {
    val parkedParsers = CountDownLatch(numBuilds + numBuilds * numTasks)
    val eooCounter = AtomicInteger()
    parser.onParserLifecycleEvent(
      beforeRead = { parkedParsers.countDown() },
      afterRead = { eooCounter.incrementAndGet() }
    )

    val buildIds = List(numBuilds) { BuildId() }
    val startBuildEvents = buildIds.map { StartBuildEvent.builder("Build", DefaultBuildDescriptor(it, "Build", "/", 0L)).build() }
    val finishBuildEvents = startBuildEvents.map { FinishBuildEvent.builder(it.id, "Build", SuccessResultImpl()).build() }
    val startEvents = List(numTasks) { taskIndex ->
      startBuildEvents.map { StartEvent.builder(StartId(), ":task$taskIndex").withParentId(it.id).build() }
    }
    val finishEvents = List(numTasks) { taskIndex ->
      startEvents[taskIndex].map { FinishEvent.builder(it.id, ":task$taskIndex", SuccessResultImpl()).withParentId(it.parentId).build() }
    }

    val dispatchers = List(numBuilds) { buildIndex ->
      GradleOutputMessageDispatcher(
        buildIds[buildIndex],
        BuildProgressListener { _, _ -> },
        listOf(parser),
        TestSettingsProvider(useSingleBuildConsole)
      )
    }

    repeat(numBuilds) { buildIndex ->
      dispatchers[buildIndex].notifyBuildEvent(buildIds[buildIndex], startBuildEvents[buildIndex])
    }
    repeat(numTasks) { taskIndex ->
      repeat(numBuilds) { buildIndex ->
        dispatchers[buildIndex].notifyBuildEvent(buildIds[buildIndex], startEvents[taskIndex][buildIndex])
      }
    }
    repeat(numTasks) { taskIndex ->
      repeat(numBuilds) { buildIndex ->
        dispatchers[buildIndex].notifyTextAvailable("> Task :task$taskIndex\n", ProcessOutputType.STDOUT)
        dispatchers[buildIndex].notifyTextAvailable("task$taskIndex output\n", ProcessOutputType.STDOUT)
      }
    }
    repeat(numTasks) { taskIndex ->
      repeat(numBuilds) { buildIndex ->
        dispatchers[buildIndex].notifyBuildEvent(buildIds[buildIndex], finishEvents[taskIndex][buildIndex])
      }
    }
    repeat(numBuilds) { buildIndex ->
      dispatchers[buildIndex].notifyTextAvailable("BUILD SUCCESSFUL in 1s\n", ProcessOutputType.STDOUT)
      dispatchers[buildIndex].notifyBuildEvent(buildIds[buildIndex], finishBuildEvents[buildIndex])
    }
    assertTrue(parkedParsers.await(5, TimeUnit.SECONDS)) {
      "A parser parked on an unfinished output holds a thread of the parser dispatcher"
    }
    repeat(numBuilds) { buildIndex ->
      dispatchers[buildIndex].close()
    }

    assertEquals(numBuilds + numBuilds * numTasks, eooCounter.get()) {
      "A parser parked on an output that is over holds a slot of the parser dispatcher"
    }
  }

  private fun expectedEvents(configure: ExpectedEventsBuilder.() -> Unit): Map<Any?, List<CollectedEvent>> =
    ExpectedEventsBuilder(useSingleBuildConsole)
      .apply(configure)
      .build()

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

    val events = CopyOnWriteArrayList<BuildEvent>()

    override fun onEvent(buildId: Any, event: BuildEvent) {
      events.add(event)
    }
  }

  private sealed interface CollectedEvent {
    data class Event(val event: BuildEvent) : CollectedEvent
    data class Output(val message: String) : CollectedEvent
    data class OutputReference(val outputs: List<Output>) : CollectedEvent
    data class Message(val kind: MessageEvent.Kind, val message: String) : CollectedEvent
  }

  private class ExpectedEventsBuilder(private val useSingleBuildConsole: Boolean) {

    private val nodes = LinkedHashMap<Any?, List<CollectedEvent>>()

    fun root(configure: ExpectedNodeBuilder.() -> Unit) {
      nodes[null] = ExpectedNodeBuilder(useSingleBuildConsole).apply(configure).build()
    }

    fun node(parentId: Any, configure: ExpectedNodeBuilder.() -> Unit) {
      nodes[parentId] = ExpectedNodeBuilder(useSingleBuildConsole).apply(configure).build()
    }

    fun build(): Map<Any?, List<CollectedEvent>> = nodes
  }

  private class ExpectedNodeBuilder(private val useSingleBuildConsole: Boolean) {

    private val events = ArrayList<CollectedEvent>()

    fun event(event: BuildEvent) {
      events.add(CollectedEvent.Event(event))
    }

    fun outputEvent(message: String) {
      events.add(CollectedEvent.Output(message))
    }

    fun outputReferenceEvent(configure: ExpectedOutputReferenceBuilder.() -> Unit) {
      events.add(ExpectedOutputReferenceBuilder().apply(configure).build())
    }

    fun messageEvent(kind: MessageEvent.Kind, message: String) {
      events.add(CollectedEvent.Message(kind, message))
    }

    fun taskOutput(message: String) {
      when (useSingleBuildConsole) {
        true -> outputReferenceEvent { outputEvent(message) }
        else -> outputEvent(message)
      }
    }

    fun build(): List<CollectedEvent> = events
  }

  private class ExpectedOutputReferenceBuilder {

    private val events = ArrayList<CollectedEvent.Output>()

    fun outputEvent(message: String) {
      events.add(CollectedEvent.Output(message))
    }

    fun build(): CollectedEvent.OutputReference = CollectedEvent.OutputReference(events)
  }

  companion object {

    // `10 builds x 10 tasks` creates 110 dispatchers, one hanging parser each. That is above the 64
    // permits of Dispatchers.IO. The suspendable parser must park without a thread, so all parsers park together.
    @JvmStatic
    fun hangingParsersArguments(): List<Arguments> {
      val parserProviders: List<() -> TestBuildOutputParser> = listOf({ SuspendableParser() }, { BlockingParser() })
      return listOf(1, 10).flatMap { numBuilds ->
        listOf(1, 10).flatMap { numTasks ->
          parserProviders.map { parserProvider -> Arguments.of(numBuilds, numTasks, parserProvider()) }
        }
      }
    }

    private fun assertEvents(expected: Map<Any?, List<CollectedEvent>>, actual: List<BuildEvent>) {
      assertEquals(expected, actual.toCollectedEvents())
    }

    private fun List<BuildEvent>.toCollectedEvents(): Map<Any?, List<CollectedEvent>> {
      val outputEvents = filterIsInstance<OutputBuildEvent>()
        .associate { it.id to CollectedEvent.Output(it.message) }
      return groupBy({ it.parentId }) { event ->
        when (event) {
          is OutputReferenceEvent -> CollectedEvent.OutputReference(event.outputIds.map { outputEvents.getValue(it) })
          is OutputBuildEvent -> CollectedEvent.Output(event.message)
          is MessageEvent -> CollectedEvent.Message(event.kind, event.message)
          else -> CollectedEvent.Event(event)
        }
      }
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
