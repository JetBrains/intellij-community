// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JPanel

@TestApplication
internal class AbstractPopupFinalRunnableTest {
  @Test
  fun `a final runnable that throws a PCE logs no error`(): Unit = timeoutRunBlocking {
    val errors = disposePopupWithFinalRunnable { throw ProcessCanceledException() }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `a final runnable that throws a CancellationException logs no error`(): Unit = timeoutRunBlocking {
    val errors = disposePopupWithFinalRunnable { throw CancellationException() }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `a final runnable that fails logs its exception`(): Unit = timeoutRunBlocking {
    val failure = IllegalStateException("final runnable failed")
    val errors = disposePopupWithFinalRunnable { throw failure }

    assertThat(errors).containsExactly(failure)
  }
}

/** Disposes a popup that has [finalRunnable], and returns the errors that the disposal logs. */
private suspend fun disposePopupWithFinalRunnable(finalRunnable: () -> Unit): List<Throwable> {
  val errors = CopyOnWriteArrayList<Throwable>()
  var isRun = false
  withContext(Dispatchers.EDT) {
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(JPanel(), null).createPopup()
    popup.setFinalRunnable {
      isRun = true
      finalRunnable()
    }
    LoggedErrorProcessor.executeWith(ErrorCollector(errors)).use {
      Disposer.dispose(popup)
    }
  }
  assertThat(isRun).isTrue()
  return errors
}

private class ErrorCollector(private val errors: MutableList<Throwable>) : LoggedErrorProcessor() {
  override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
    errors.add(t ?: AssertionError(message))
    return Action.NONE
  }
}
