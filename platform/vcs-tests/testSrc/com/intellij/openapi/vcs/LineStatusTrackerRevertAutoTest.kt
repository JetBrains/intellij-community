// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.diff.DiffTestCase
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.UIUtil
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class LineStatusTrackerRevertAutoTest : BaseLineStatusTrackerTestCase() {
  companion object {
    private val LOG = Logger.getInstance(LineStatusTrackerRevertAutoTest::class.java)

    private const val TEST_RUNS = 100
    private const val MODIFICATIONS = 10
    private const val TEXT_LENGTH = 10
    private const val CHANGE_LENGTH = 10
  }

  private lateinit var myRng: Random

  fun testSimple() {
    doTest(System.currentTimeMillis(), TEST_RUNS, MODIFICATIONS, TEXT_LENGTH, CHANGE_LENGTH, -1, false)
  }

  fun testComplex() {
    doTest(System.currentTimeMillis(), TEST_RUNS, MODIFICATIONS, TEXT_LENGTH, CHANGE_LENGTH, 5, false)
  }

  fun testInitial() {
    doTestInitial(System.currentTimeMillis(), TEST_RUNS, TEXT_LENGTH, false)
  }

  fun testSimpleSmart() {
    doTest(System.currentTimeMillis(), TEST_RUNS, MODIFICATIONS, TEXT_LENGTH, CHANGE_LENGTH, -1, true)
  }

  fun testComplexSmart() {
    doTest(System.currentTimeMillis(), TEST_RUNS, MODIFICATIONS, TEXT_LENGTH, CHANGE_LENGTH, 5, true)
  }

  fun testInitialSmart() {
    doTestInitial(System.currentTimeMillis(), TEST_RUNS, TEXT_LENGTH, true)
  }

  fun testUnfreeze() {
    doTestUnfreeze(System.currentTimeMillis(), TEST_RUNS, TEXT_LENGTH)
  }

  private fun doTest(seed: Long, testRuns: Int, modifications: Int, textLength: Int, changeLength: Int, iterations: Int, smart: Boolean) {
    myRng = Random(seed)
    for (i in 0 until testRuns) {
      val currentSeed = getCurrentSeed()
      if (i % 1000 == 0) LOG.debug(i.toString())
      try {
        val initial = generateText(textLength)
        lightTest(initial, initial, smart) {
          // println("Initial: " + initial.replace("\n", "\\n"));

          val count = myRng.nextInt(modifications)
          for (j in 0 until count) {
            val writeChanges = myRng.nextInt(4) + 1
            runCommandVerify {
              for (k in 0 until writeChanges) {
                applyRandomChange(changeLength)
              }
            }

            verify()
          }

          if (iterations > 0) {
            checkRevertComplex(iterations)
          }
          else {
            checkRevert(tracker.getRanges()!!.size * 2)
          }
        }

        UIUtil.dispatchAllInvocationEvents()
      }
      catch (e: Throwable) {
        println("Seed: " + seed)
        println("TestRuns: " + testRuns)
        println("Modifications: " + modifications)
        println("TextLength: " + textLength)
        println("ChangeLength: " + changeLength)
        println("I: " + i)
        println("Current seed: " + currentSeed)
        throw e
      }

    }
  }

  fun doTestInitial(seed: Long, testRuns: Int, textLength: Int, smart: Boolean) {
    myRng = Random(seed)
    for (i in 0 until testRuns) {
      if (i % 1000 == 0) LOG.debug(i.toString())
      val currentSeed = getCurrentSeed()
      try {
        val initial = generateText(textLength)
        val initialVcs = generateText(textLength)
        lightTest(initial, initialVcs, smart) {
          checkRevert(tracker.getRanges()!!.size * 2)
        }

        UIUtil.dispatchAllInvocationEvents()
      }
      catch (e: Throwable) {
        println("Seed: " + seed)
        println("TestRuns: " + testRuns)
        println("TextLength: " + textLength)
        println("I: " + i)
        println("Current seed: " + currentSeed)
        throw e
      }

    }
  }

  fun doTestUnfreeze(seed: Long, testRuns: Int, textLength: Int) {
    myRng = Random(seed)
    for (i in 0 until testRuns) {
      if (i % 1000 == 0) LOG.debug(i.toString())
      val currentSeed = getCurrentSeed()

      val initial = generateText(textLength)
      val initialVcs = generateText(textLength)
      val newText = generateText(textLength)
      val newTextVcs = generateText(textLength)
      try {
        lightTest(initial, initialVcs, true) {
          tracker.doFrozen(Runnable {
            runWriteAction {
              tracker.document.setText(newText)
              simpleTracker.setBaseRevision(newTextVcs)
            }
          })
        }

        UIUtil.dispatchAllInvocationEvents()
      }
      catch (e: Throwable) {
        println("Text: " + DiffTestCase.textToReadableFormat(initial))
        println("Vcs: " + DiffTestCase.textToReadableFormat(initialVcs))
        println("New Text: " + DiffTestCase.textToReadableFormat(newText))
        println("New Vcs: " + DiffTestCase.textToReadableFormat(newTextVcs))
        println("Seed: " + seed)
        println("TestRuns: " + testRuns)
        println("TextLength: " + textLength)
        println("I: " + i)
        println("Current seed: " + currentSeed)
        throw e
      }

    }
  }

  private fun TrackerModificationsTest.checkRevert(maxIterations: Int) {
    var count = 0
    while (true) {
      if (count > maxIterations) throw Exception("Revert loop detected")
      val ranges = tracker.getRanges()!!
      if (ranges.isEmpty()) break
      val index = myRng.nextInt(ranges.size)
      val range = ranges[index]

      range.rollback()
      count++
    }
    assertEquals(document.text, vcsDocument.text)
  }

  private fun TrackerModificationsTest.checkRevertComplex(iterations: Int) {
    val lines = BitSet()

    for (i in 0 until iterations) {
      lines.clear()

      for (j in 0 until document.lineCount + 2) {
        if (myRng.nextInt(10) < 3) {
          lines.set(j)
        }
      }

      rollbackLines(lines)
    }

    lines.set(0, document.lineCount + 2)
    rollbackLines(lines)

    assertEquals(document.text, vcsDocument.text)
  }

  private fun TrackerModificationsTest.applyRandomChange(changeLength: Int) {
    val textLength = document.textLength
    val type = myRng.nextInt(3)
    val offset = if (textLength != 0) myRng.nextInt(textLength) else 0
    val length = if (textLength - offset != 0) myRng.nextInt(textLength - offset) else offset
    val data = generateText(changeLength)
    // println("Change: " + type + " - " + offset + " - " + length + " - " + data.replace("\n", "\\n"));
    when (type) {
      0 -> document.insertString(offset, data)
      1 -> document.deleteString(offset, offset + length)
      2 -> document.replaceString(offset, offset + length, data)
    }
  }

  private fun generateText(textLength: Int): String {
    val length = myRng.nextInt(textLength)
    val builder = StringBuilder(length)

    for (i in 0 until length) {
      val rnd = myRng.nextInt(10)
      if (rnd == 0) {
        builder.append(' ')
      }
      else if (rnd < 7) {
        builder.append(rnd.toString())
      }
      else {
        builder.append('\n')
      }
    }

    return builder.toString()
  }

  private fun getCurrentSeed(): Long {
    val seedField = myRng.javaClass.getDeclaredField("seed")
    seedField.isAccessible = true
    val seedFieldValue = seedField.get(myRng) as AtomicLong
    return seedFieldValue.get() xor 0x5DEECE66DL
  }
}
