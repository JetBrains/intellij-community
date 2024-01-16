// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressState
import com.intellij.platform.util.progress.impl.TextDetailsProgressReporter
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.init
import com.intellij.util.containers.tail
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows

fun progressReporterTest(
  vararg expectedUpdates: ProgressState,
  action: suspend CoroutineScope.() -> Unit,
) = timeoutRunBlocking {
  val actualUpdates = ContainerUtil.createConcurrentList<ProgressState>()
  val progressReporter = TextDetailsProgressReporter(this)
  val collector = launch(Dispatchers.Unconfined + CoroutineName("state collector")) {
    progressReporter.progressState.collect { state ->
      actualUpdates.add(state)
    }
  }
  withContext(progressReporter.asContextElement(), action)
  progressReporter.close()
  progressReporter.awaitCompletion()
  collector.cancelAndJoin()
  assertEquals(ProgressState(null, null, -1.0), actualUpdates.first())
  assertEquals(ProgressState(null, null, 1.0), actualUpdates.last())
  assertOrderedEquals(actualUpdates.toList().init().tail(), expectedUpdates.toList())
}

internal inline fun <reified T : Throwable> assertLogThrows(executable: () -> Unit): T {
  return assertThrows<T> {
    val loggerError = assertThrows<TestLoggerFactory.TestLoggerAssertionError>(executable)
    throw requireNotNull(loggerError.cause)
  }
}
