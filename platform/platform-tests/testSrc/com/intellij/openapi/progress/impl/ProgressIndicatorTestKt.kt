// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.openapi.progress.impl

import com.intellij.idea.TestFor
import com.intellij.openapi.progress.forEachWithProgress
import com.intellij.openapi.progress.runUnderBoundCancellation
import com.intellij.openapi.progress.runUnderNestedProgressAndRelayMessages
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import org.junit.Assert
import org.junit.Test

private fun fractionToText(fraction: Double) = "[" + (fraction * 1000).toLong().div(1000.0).toString() + "]"

private fun ProgressIndicatorBase.assertStatus(expected: String) {

  val progress = when {
    isIndeterminate -> "[running]"
    else -> {
      fractionToText(fraction)
    }
  }

  val status = (text ?: "<null>") + " / " + (text2 ?: "<null>")
  val actual = "$progress $status"
  Assert.assertEquals(expected, actual)
}

class ProgressIndicatorTestKt {
  private val parentProgress = ProgressIndicatorBase().apply {
    text = "A"
    text2 = "b"
  }

  private fun assertStatus(expected: String) = parentProgress.assertStatus(expected)

  private val emptyList = listOf<String>()
  private val singleList = listOf("a")
  private val twoList = listOf("a", "b")
  private val threeList = listOf("a", "b", "c")
  private val fourList = listOf("a", "b", "c", "d")

  @Test
  fun testFullSubTask() {
    singleList.forEachWithProgress(parentProgress) { _, it ->
      assertStatus("[0.0] A / b")

      it.fraction = 0.5
      it.text = "text1"
      it.text2 = "text2"

      assertStatus("[0.5] text1 / text2")
    }
  }

  @Test
  fun testHalfSubTask() {
    twoList.forEachWithProgress(parentProgress) { y, it ->
      if (y == "a") {
        assertStatus("[0.0] A / b")
        it.fraction = 0.5
        it.text = "text1"
        it.text2 = "text2"
        assertStatus("[0.25] text1 / text2")
      }
    }
  }

  @Test
  fun testLinearFractions() {
    threeList.withIndex().toList().forEachWithProgress(parentProgress) { (y1, _), it1 ->
      val v = fractionToText(y1.toDouble() / 3)
      assertStatus("$v A / b")
    }
  }


  @Test
  fun testSimpleForEach() {
    listOf(
      "[0.0]",
      "[0.333]",
      "[0.666]"
    ).forEachWithProgress(parentProgress) { y, it ->
      assertStatus("$y A / b")
    }
  }

  @Test
  fun testNestedLinearFractions() {
    listOf(
      "[0.0]" to listOf("[0.0]", "[0.111]", "[0.222]"),
      "[0.333]" to listOf("[0.333]", "[0.5]"),
      "[0.666]" to listOf("[0.666]", "[0.75]", "[0.833]", "[0.916]")
    ).forEachWithProgress(parentProgress) { (y, next), it ->
      assertStatus("$y A / b")
      next.forEachWithProgress(it) { y2, _ ->
        assertStatus("$y2 A / b")
      }
    }
  }

  @Test
  fun testSeveralHalfSubTask() {
    threeList.forEachWithProgress(parentProgress) { y, it ->
      if (y == "b") {
        assertStatus("[0.333] A / b")
        it.fraction = 0.5
        it.text = "text1"
        it.text2 = "text2"
        assertStatus("[0.5] text1 / text2")
      }
    }
  }

  @Test
  fun testNestedHalfSubTask() {
    threeList.forEachWithProgress(parentProgress) { y1, it1 ->
      if (y1 == "b") {
        threeList.forEachWithProgress(it1) { y2, it2 ->
          if (y2 == "b") {
            assertStatus("[0.444] A / b")

            it2.fraction = 0.5
            it2.text = "text1"
            it2.text2 = "text2"
            assertStatus("[0.5] text1 / text2")
          }
        }
        assertStatus("[0.333] A / b")
      }
    }
  }

  @Test
  @TestFor(issues = ["IDEA-257352"])
  fun testNestedProgressShouldKeepFraction() {
    val indicatorProgress = ProgressIndicatorBase()

    runUnderNestedProgressAndRelayMessages(parentProgress, indicatorProgress) {
      parentProgress.text = "1"
      parentProgress.text2 = "2"
      indicatorProgress.assertStatus("[running] 1 / 2")

      threeList.forEachWithProgress(parentProgress) { el, subProgress ->
        subProgress.fraction = 0.5
        subProgress.text = "e-$el"
        subProgress.text2 = "2-$el"

        indicatorProgress.assertStatus(when(el) {
          "a" -> "[0.166] e-a / 2-a"
          "b" -> "[0.5] e-b / 2-b"
          "c" -> "[0.833] e-c / 2-c"
          else -> error("Unexpected case")
        })
      }

      indicatorProgress.assertStatus("[running] 1 / 2")
    }

    indicatorProgress.assertStatus("[running] 1 / 2")
  }
}
