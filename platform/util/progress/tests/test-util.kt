// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

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
  vararg expectedUpdates: ExpectedState,
  action: suspend CoroutineScope.() -> Unit,
) = timeoutRunBlocking {
  val actualUpdates = ContainerUtil.createConcurrentList<ExpectedState>()
  val pipe = (this + Dispatchers.Unconfined).createProgressPipe()
  val collector = launch(Dispatchers.Unconfined) {
    pipe.progressUpdates().collect {
      actualUpdates.add(ExpectedState(it.fraction, it.text, it.details))
    }
  }
  try {
    pipe.collectProgressUpdates(action)
  }
  finally {
    collector.cancelAndJoin()
  }
  assertEquals(ExpectedState(null, null, null), actualUpdates.first())
  assertEquals(ExpectedState(fraction = 1.0, text = null, details = null), actualUpdates.last())
  assertOrderedEquals(actualUpdates.toList().init().tail(), expectedUpdates.toList())
}

internal inline fun <reified T : Throwable> assertLogThrows(executable: () -> Unit): T {
  return assertThrows<T> {
    val loggerError = assertThrows<TestLoggerFactory.TestLoggerAssertionError>(executable)
    throw requireNotNull(loggerError.cause)
  }
}
