// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.ScalableIcon
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.Icon

/**
 * A scaled copy of a deferred icon gives a scaled result. The deferrer keeps one icon for each parameter, and each caller
 * with that parameter gets it. So the deferrer must keep the result of a scaled copy without the scale of the copy.
 */
@TestApplication
internal class DeferredIconScaleTest {
  @Test
  fun `the deferrer keeps the result of a scaled copy without the scale`(): Unit = timeoutRunBlocking {
    val parameter = Any()
    val deferred = defer(parameter) as DeferredIconImpl<*>

    val scaled = deferred.scale(2f)
    scaled.awaitEvaluation()

    assertThat(scaled.iconWidth).describedAs("the scaled copy paints its scaled result").isEqualTo(2 * EVALUATED_SIZE)
    val next = defer(parameter)
    assertThat((next as ScalableIcon).scale).describedAs("the scale of the next icon of the same parameter").isEqualTo(1f)
    assertThat(next.iconWidth).describedAs("the width of the next icon of the same parameter").isEqualTo(EVALUATED_SIZE)
    assertThat(next.iconHeight).describedAs("the height of the next icon of the same parameter").isEqualTo(EVALUATED_SIZE)
  }

  @Test
  fun `the result of an icon with no scale goes to the cache of the deferrer`(): Unit = timeoutRunBlocking {
    val parameter = Any()
    val deferred = defer(parameter) as DeferredIconImpl<*>

    deferred.awaitEvaluation()

    assertThat(defer(parameter)).isSameAs(EVALUATED)
  }

  private fun defer(parameter: Any): Icon = IconDeferrer.getInstance().defer(null, parameter) { EVALUATED }

  private companion object {
    const val EVALUATED_SIZE = 20
    val EVALUATED: Icon = EmptyIcon.create(EVALUATED_SIZE)
  }
}
