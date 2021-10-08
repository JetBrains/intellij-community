// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.util.ui.Animator
import javax.swing.JScrollBar
import kotlin.math.cos
import kotlin.math.roundToInt

internal class StepAnimator(val verticalScrollBar: JScrollBar, val messagePane: LessonMessagePane) {
  private var animator: Animator? = null

  private val totalMessageAnimationCycles = 15

  fun startAnimation(needScroll: Int) {
    animator?.let {
      it.suspend()
      stopMessageAnimation()
    }

    messagePane.totalAnimation = totalMessageAnimationCycles
    messagePane.currentAnimation = 0

    scrollAnimation(needScroll)
  }

  private fun scrollAnimation(needScrollTo: Int) {
    val start = verticalScrollBar.value
    if (needScrollTo <= start) {
      rectangleAnimation()
      verticalScrollBar.value = needScrollTo
      return
    }
    val needAdd = needScrollTo - start
    animator = object : Animator("Scroll animation", needAdd, 200, false) {
      override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
        verticalScrollBar.value = start + (totalFrames * (1 - cos(Math.PI * (frame.toDouble() / totalFrames))) / 2).roundToInt() //0..1
      }

      override fun paintCycleEnd() {
        if (this == animator) {
          verticalScrollBar.value = needScrollTo
          animator = null
          rectangleAnimation()
        }
      }
    }
    animator?.resume()
  }

  private fun rectangleAnimation() {
    animator = object : Animator("Scroll animation", totalMessageAnimationCycles, 100, false) {
      override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
        messagePane.totalAnimation = totalFrames
        messagePane.currentAnimation = frame
        messagePane.repaint()
      }

      override fun paintCycleEnd() {
        if (this == animator) {
          stopMessageAnimation()
          messagePane.repaint()
          animator = null
        }
      }
    }
    animator?.resume()
  }

  private fun stopMessageAnimation() {
    messagePane.totalAnimation = 0
    messagePane.currentAnimation = 0
  }
}