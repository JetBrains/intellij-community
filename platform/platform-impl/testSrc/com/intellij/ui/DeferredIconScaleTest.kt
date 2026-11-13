// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBScalableIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import javax.swing.Icon

/**
 * A scaled copy of a deferred icon paints a scaled result. The deferrer keeps one icon for each parameter, and each caller
 * with that parameter gets it. So the deferrer must keep the result of the evaluator, before the scale of the copy.
 */
@TestApplication
internal class DeferredIconScaleTest {
  @Test
  fun `the deferrer keeps the result of a scaled copy without the scale`(): Unit = timeoutRunBlocking {
    checkTheDeferrerKeepsTheEvaluatedIcon(EmptyIcon.create(EVALUATED_SIZE))
  }

  @Test
  fun `the deferrer keeps the image icon of a scaled copy without the scale`(): Unit = timeoutRunBlocking {
    // An icon of `AllIcons` is a dummy icon in a test, so the test loads the image icon itself, with the icon loader on.
    IconLoader.activate()
    try {
      val evaluated = IconLoader.getIcon("/general/gearPlain.svg", AllIcons::class.java.classLoader)
      assertThat(evaluated).describedAs("the scale method of this class gives the same icon for the scale 1")
        .isInstanceOf(CachedImageIcon::class.java)

      checkTheDeferrerKeepsTheEvaluatedIcon(evaluated)
    }
    finally {
      if (GraphicsEnvironment.isHeadless()) {
        IconLoader.deactivate()
      }
    }
  }

  @Test
  fun `a scaled copy paints the scaled result of an icon that changes itself in the scale method`(): Unit = timeoutRunBlocking {
    val deferred = defer(Any(), SelfScalingIcon()) as DeferredIconImpl<*>

    val scaled = deferred.scale(2f)
    scaled.awaitEvaluation()

    assertThat(scaled.iconWidth).describedAs("the width of the scaled copy").isEqualTo(2 * EVALUATED_SIZE)
  }

  @Test
  fun `the result of an icon with no scale goes to the cache of the deferrer`(): Unit = timeoutRunBlocking {
    val parameter = Any()
    val evaluated = EmptyIcon.create(EVALUATED_SIZE)
    val deferred = defer(parameter, evaluated) as DeferredIconImpl<*>

    deferred.awaitEvaluation()

    assertThat(defer(parameter, evaluated)).isSameAs(evaluated)
  }

  private suspend fun checkTheDeferrerKeepsTheEvaluatedIcon(evaluated: Icon) {
    val parameter = Any()
    val width = evaluated.iconWidth
    val deferred = defer(parameter, evaluated) as DeferredIconImpl<*>

    val scaled = deferred.scale(2f)
    scaled.awaitEvaluation()

    assertThat(scaled.iconWidth).describedAs("the scaled copy paints its scaled result").isEqualTo(2 * width)
    val next = defer(parameter, evaluated)
    assertThat(next.iconWidth).describedAs("the width of the next icon of the same parameter").isEqualTo(width)
    assertThat(next).describedAs("the next icon of the same parameter").isSameAs(evaluated)
  }

  private fun defer(parameter: Any, evaluated: Icon): Icon = IconDeferrer.getInstance().defer(null, parameter) { evaluated }

  /** Keeps the scale method of [JBScalableIcon]: it sets the scale of this icon and returns this icon. */
  private class SelfScalingIcon : JBScalableIcon() {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {}

    override fun getIconWidth(): Int = scaleVal(EVALUATED_SIZE.toDouble(), ScaleType.OBJ_SCALE).toInt()

    override fun getIconHeight(): Int = iconWidth
  }

  private companion object {
    const val EVALUATED_SIZE = 20
  }
}
