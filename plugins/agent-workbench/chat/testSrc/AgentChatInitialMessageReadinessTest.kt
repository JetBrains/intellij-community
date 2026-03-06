// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class AgentChatInitialMessageReadinessTest {
  @Test
  fun bufferedMeaningfulOutputBecomesReadyAfterIdleWindow(): Unit = timeoutRunBlocking {
    val fixture = ReadinessFixture()
    var notifications = 0
    fixture.regularOutputModel.update(0, "ready")

    val result = fixture.awaitReadiness(
      timeoutMs = 500,
      idleMs = 20,
      onMeaningfulOutput = { notifications++ },
    )

    assertThat(result).isEqualTo(AgentChatTerminalInputReadiness.READY)
    assertThat(notifications).isEqualTo(1)
  }

  @Test
  fun noMeaningfulOutputTimesOut(): Unit = timeoutRunBlocking {
    val result = ReadinessFixture().awaitReadiness(timeoutMs = 40, idleMs = 10)

    assertThat(result).isEqualTo(AgentChatTerminalInputReadiness.TIMEOUT)
  }

  @Test
  fun delayedMeaningfulOutputBecomesReady(): Unit = timeoutRunBlocking {
    val fixture = ReadinessFixture()
    var notifications = 0
    val readiness = async {
      fixture.awaitReadiness(
        timeoutMs = 500,
        idleMs = 20,
        onMeaningfulOutput = { notifications++ },
      )
    }

    delay(20.milliseconds)
    fixture.regularOutputModel.update(0, "hello")

    assertThat(readiness.await()).isEqualTo(AgentChatTerminalInputReadiness.READY)
    assertThat(notifications).isEqualTo(1)
  }

  @Test
  fun outputAcrossBothModelsResetsIdleWindow(): Unit = timeoutRunBlocking {
    val fixture = ReadinessFixture()
    var notifications = 0
    val readiness = async {
      fixture.awaitReadiness(
        timeoutMs = 500,
        idleMs = 120,
        onMeaningfulOutput = { notifications++ },
      )
    }

    delay(10.milliseconds)
    fixture.regularOutputModel.update(0, "first")
    delay(70.milliseconds)
    fixture.alternativeOutputModel.update(0, "second")
    delay(70.milliseconds)

    assertThat(readiness.isCompleted).isFalse()
    assertThat(readiness.await()).isEqualTo(AgentChatTerminalInputReadiness.READY)
    assertThat(notifications).isEqualTo(2)
  }

  @Test
  fun terminationBeforeOutputReturnsTerminated(): Unit = timeoutRunBlocking {
    val fixture = ReadinessFixture()
    val readiness = async {
      fixture.awaitReadiness(timeoutMs = 500, idleMs = 20)
    }

    delay(20.milliseconds)
    fixture.sessionState.value = TerminalViewSessionState.Terminated

    assertThat(readiness.await()).isEqualTo(AgentChatTerminalInputReadiness.TERMINATED)
  }

  @Test
  fun terminationDuringIdleWindowReturnsTerminated(): Unit = timeoutRunBlocking {
    val fixture = ReadinessFixture()
    var notifications = 0
    val readiness = async {
      fixture.awaitReadiness(
        timeoutMs = 500,
        idleMs = 120,
        onMeaningfulOutput = { notifications++ },
      )
    }

    delay(10.milliseconds)
    fixture.regularOutputModel.update(0, "hello")
    delay(40.milliseconds)
    fixture.sessionState.value = TerminalViewSessionState.Terminated

    assertThat(readiness.await()).isEqualTo(AgentChatTerminalInputReadiness.TERMINATED)
    assertThat(notifications).isEqualTo(1)
  }

  @Test
  fun timedOutWaitDoesNotRetainOutputListeners(): Unit = timeoutRunBlocking {
    val fixture = ReadinessFixture()
    var notifications = 0

    val firstResult = fixture.awaitReadiness(
      timeoutMs = 40,
      idleMs = 10,
      onMeaningfulOutput = { notifications++ },
    )

    assertThat(firstResult).isEqualTo(AgentChatTerminalInputReadiness.TIMEOUT)

    val secondReadiness = async {
      fixture.awaitReadiness(
        timeoutMs = 500,
        idleMs = 20,
        onMeaningfulOutput = { notifications++ },
      )
    }

    delay(20.milliseconds)
    fixture.regularOutputModel.update(0, "hello")

    assertThat(secondReadiness.await()).isEqualTo(AgentChatTerminalInputReadiness.READY)
    assertThat(notifications).isEqualTo(1)
  }

  @Test
  fun meaningfulOutputChangeFilterRejectsWhitespaceTypeAheadAndTrimming() {
    val model = createOutputModel()

    assertThat(isMeaningfulTerminalOutputChange(createEvent(model, "  \n", isTypeAhead = false, isTrimming = false))).isFalse()
    assertThat(isMeaningfulTerminalOutputChange(createEvent(model, "hello", isTypeAhead = true, isTrimming = false))).isFalse()
    assertThat(isMeaningfulTerminalOutputChange(createEvent(model, "", isTypeAhead = false, isTrimming = true))).isFalse()
    assertThat(isMeaningfulTerminalOutputChange(createEvent(model, "hello", isTypeAhead = false, isTrimming = false))).isTrue()
  }
}

private class ReadinessFixture(
  val regularOutputModel: MutableTerminalOutputModelImpl = createOutputModel(),
  val alternativeOutputModel: MutableTerminalOutputModelImpl = createOutputModel(),
  val sessionState: MutableStateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.Running),
) {
  suspend fun awaitReadiness(
    timeoutMs: Long,
    idleMs: Long,
    onMeaningfulOutput: () -> Unit = {},
  ): AgentChatTerminalInputReadiness {
    return awaitTerminalInitialMessageReadiness(
      sessionState = sessionState,
      regularOutputModel = regularOutputModel,
      alternativeOutputModel = alternativeOutputModel,
      timeoutMs = timeoutMs,
      idleMs = idleMs,
      onMeaningfulOutput = onMeaningfulOutput,
    )
  }
}

private fun createOutputModel(maxLength: Int = 0): MutableTerminalOutputModelImpl {
  return MutableTerminalOutputModelImpl(EditorFactory.getInstance().createDocument(""), maxLength)
}

private suspend fun MutableTerminalOutputModelImpl.update(lineIndex: Long, text: String) {
  withContext(Dispatchers.EDT) {
    CommandProcessor.getInstance().runUndoTransparentAction {
      runWriteAction {
        updateContent(lineIndex, text, emptyList())
      }
    }
  }
}

private fun createEvent(
  model: TerminalOutputModel,
  newText: String,
  isTypeAhead: Boolean,
  isTrimming: Boolean,
): TerminalContentChangeEvent {
  return object : TerminalContentChangeEvent {
    override val model: TerminalOutputModel = model
    override val offset = model.startOffset
    override val oldText: CharSequence = ""
    override val newText: CharSequence = newText
    override val isTypeAhead: Boolean = isTypeAhead
    override val isTrimming: Boolean = isTrimming
  }
}
