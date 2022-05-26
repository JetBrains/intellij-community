// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.vcs.annotate.AnnotatedLineModificationDetails.InnerChange
import com.intellij.openapi.vcs.annotate.AnnotatedLineModificationDetails.InnerChangeType
import com.intellij.openapi.vcs.annotate.DefaultLineModificationDetailsProvider
import com.intellij.testFramework.ApplicationExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class AnnotateLineModificationDetailsTest {
  companion object {
    @JvmField
    @RegisterExtension
    val appRule = ApplicationExtension()
  }

  @Test
  fun testSimpleModifications() {
    doTest("A B C D E",
           "A B X D E",
           0,
           "A B [X] D E")

    doTest("A_B C D_E",
           "A_B X D_E",
           1,
           "B [X] D")

    doTest("A_B C D_E",
           "A_B D_E",
           1,
           "B| D")

    doTest("A_B D_E",
           "A_B X D_E",
           1,
           "B< X> D")

    doTest("A B C D E",
           "A X C Y E",
           0,
           "A [X] C [Y] E")

    doTest("A B_C D_E F",
           "A1 B_C X D_E F1",
           1,
           "C< X> D")

    doTest("A B_C D_E F",
           "A1 B_C D_E F1",
           1,
           null)

    doTest("A B_E F",
           "A1 B_C D_E F1",
           1,
           "<C D>")
  }

  @Test
  fun testMultilineModifications() {
    doTest("A B C D E",
           "A_B C D_E",
           1,
           "|B C D|")

    doTest("A_B C D_E",
           "A B C D E",
           0,
           "A[ ]B C D[ ]E")

    doTest("X X A_B C D_E",
           "Y X_A B C D E",
           1,
           "<A >B C D[ ]E")

    doTest("X X A_B C D_E X X",
           "X Y_Y B C Y_Y X",
           1,
           "<Y >B C [Y]")
  }


  private fun doTest(textBefore: String, textAfter: String, line: Int,
                     expected: String?) {
    val details = DefaultLineModificationDetailsProvider
      .createDetailsFor(textBefore.replace('_', '\n'),
                        textAfter.replace('_', '\n'),
                        line)
    if (expected != null) {
      val (expectedLine, changes) = parseExpected(expected)
      Assertions.assertEquals(expectedLine, details!!.lineContentAfter)
      Assertions.assertEquals(changes.map { TestChange(it) }, details.changes.map { TestChange(it) })
    }
    else {
      Assertions.assertNull(details)
    }
  }

  private fun parseExpected(expected: String): Pair<String, List<InnerChange>> {
    val sb = StringBuilder()
    val changes = mutableListOf<InnerChange>()

    var index = 0
    var type: InnerChangeType? = null
    var lastIndex = 0
    for (char in expected) {
      when (char) {
        '|' -> changes += InnerChange(index, index, InnerChangeType.DELETED)
        '[' -> {
          type = InnerChangeType.MODIFIED
          lastIndex = index
        }
        '<' -> {
          type = InnerChangeType.INSERTED
          lastIndex = index
        }
        ']', '>' -> {
          changes += InnerChange(lastIndex, index, type!!)
          type = null
        }
        else -> {
          sb.append(char)
          index++
        }
      }
    }

    return Pair(sb.toString(), changes)
  }

  private data class TestChange(val startOffset: Int, val endOffset: Int, val type: InnerChangeType) {
    constructor(change: InnerChange) : this(change.startOffset, change.endOffset, change.type)

    override fun toString(): String = "[$startOffset, $endOffset) - $type"
  }
}