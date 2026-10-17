// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ref.GCWatcher
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A done deferred icon keeps no parameter of its evaluator, such as a request with a PSI pointer, and it keeps its identity:
 * its hash code stays the same, and it equals an icon of the same parameter while that parameter lives.
 */
@TestApplication
internal class DeferredIconParamTest {
  @Test
  fun `a done icon keeps no parameter of its evaluator`(): Unit = timeoutRunBlocking {
    val (icon, parameter) = iconWithWatchedParameter()
    icon.awaitEvaluation()

    parameter.ensureCollected()
    assertThat(icon.retrieveIcon()).isSameAs(EmptyIcon.ICON_16)
  }

  @Test
  fun `a done icon keeps its hash code and equals an icon of the same live parameter`(): Unit = timeoutRunBlocking {
    val parameter = "the parameter"
    val done = deferred(parameter)
    val hash = done.hashCode()

    done.awaitEvaluation()

    val pending = deferred(parameter)
    assertThat(done.hashCode()).isEqualTo(hash).isEqualTo(pending.hashCode())
    assertThat(done).isEqualTo(pending)
    assertThat(pending).isEqualTo(done)
  }

  /** Not a suspend function, so no coroutine frame keeps the parameter after the return. */
  private fun iconWithWatchedParameter(): Pair<DeferredIconImpl<Any>, GCWatcher> {
    val parameter = Any()
    return deferred(parameter) to GCWatcher.tracking(parameter)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> deferred(parameter: T): DeferredIconImpl<T> {
    return DeferredIconImpl.withoutReadAction(null, parameter) { EmptyIcon.ICON_16 } as DeferredIconImpl<T>
  }
}
