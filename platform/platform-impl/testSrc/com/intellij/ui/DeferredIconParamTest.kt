// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ref.GCWatcher
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import javax.swing.Icon

/**
 * A done deferred icon keeps no parameter of its evaluator, such as a request with a PSI pointer, and no code can read it then.
 * The icon still gives its evaluated icon, and its hash code stays the same. It equals an icon of the same parameter while that
 * parameter lives, and only itself after that.
 */
@TestApplication
internal class DeferredIconParamTest {
  @Test
  fun `a done icon keeps no parameter of its evaluator`(): Unit = timeoutRunBlocking {
    val (icon, parameter) = iconWithWatchedParameter { Any() }
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

  @Test
  fun `a done icon gives its evaluated icon to evaluate and to retrieveIcon off the EDT`(): Unit = timeoutRunBlocking {
    val evaluated = EmptyIcon.create(7)
    val icon = deferred(Any(), evaluated)
    icon.awaitEvaluation()

    assertThat(icon.evaluate()).isSameAs(evaluated)
    assertThat(withContext(Dispatchers.Default) { icon.retrieveIcon() }).isSameAs(evaluated)
  }

  @Test
  fun `a read of the parameter of a done icon fails`(): Unit = timeoutRunBlocking {
    val icon = deferred(Any())
    icon.awaitEvaluation()

    assertThatThrownBy { icon.evaluationParam }.isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun `a done icon whose parameter is collected equals only itself`(): Unit = timeoutRunBlocking {
    val (done, parameter) = iconWithWatchedParameter { EqualParameter("the parameter") }
    done.awaitEvaluation()
    parameter.ensureCollected()

    val pending = deferred(EqualParameter("the parameter"))
    assertThat(done).isEqualTo(done)
    assertThat(done).isNotEqualTo(pending)
    assertThat(pending).isNotEqualTo(done)
  }

  /** Not a suspend function, so no coroutine frame keeps the parameter after the return. */
  private fun <T : Any> iconWithWatchedParameter(create: () -> T): Pair<DeferredIconImpl<T>, GCWatcher> {
    val parameter = create()
    return deferred(parameter) to GCWatcher.tracking(parameter)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> deferred(parameter: T, evaluated: Icon = EmptyIcon.ICON_16): DeferredIconImpl<T> {
    return DeferredIconImpl.withoutReadAction(null, parameter) { evaluated } as DeferredIconImpl<T>
  }
}

/** A parameter that equals another instance with the same name, as an icon request of the same element does. */
private data class EqualParameter(val name: String)
