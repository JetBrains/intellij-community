// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnsSizeCalculatorTest {

  @Test
  fun testColumnsSizeCalculator() {
    testColumnsSizeCalculator(arrayOf(), 100, setOf(), arrayOf(0), 0)

    testColumnsSizeCalculator(arrayOf(0, 1, 10), 100, setOf(0), arrayOf(0, 100), 10)
    testColumnsSizeCalculator(arrayOf(0, 1, 10), 100, setOf(), arrayOf(0, 10), 10)

    var constraints = arrayOf(
      0, 1, 10,
      1, 1, 10,
      2, 1, 10,
    )
    testColumnsSizeCalculator(constraints, 90, setOf(), arrayOf(0, 10, 20, 30), 30)
    testColumnsSizeCalculator(constraints, 90, setOf(0), arrayOf(0, 70, 80, 90), 30)
    testColumnsSizeCalculator(constraints, 90, setOf(1), arrayOf(0, 10, 80, 90), 30)
    testColumnsSizeCalculator(constraints, 90, setOf(2), arrayOf(0, 10, 20, 90), 30)
    testColumnsSizeCalculator(constraints, 90, setOf(0, 1, 2), arrayOf(0, 30, 60, 90), 30)

    constraints = arrayOf(
      0, 1, 10,
      1, 1, 10,
      0, 2, 50,
    )
    testColumnsSizeCalculator(constraints, 100, setOf(), arrayOf(0, 10, 50), 50)
    testColumnsSizeCalculator(constraints, 100, setOf(0), arrayOf(0, 90, 100), 50)
    testColumnsSizeCalculator(constraints, 100, setOf(1), arrayOf(0, 10, 100), 50)
    testColumnsSizeCalculator(constraints, 100, setOf(0, 1), arrayOf(0, 50, 100), 50)

    constraints = arrayOf(
      0, 1, 20,
      1, 1, 20,
      0, 2, 10,
    )
    testColumnsSizeCalculator(constraints, 100, setOf(), arrayOf(0, 20, 40), 40)
    testColumnsSizeCalculator(constraints, 100, setOf(0), arrayOf(0, 80, 100), 40)
    testColumnsSizeCalculator(constraints, 100, setOf(1), arrayOf(0, 20, 100), 40)
    testColumnsSizeCalculator(constraints, 100, setOf(0, 1), arrayOf(0, 50, 100), 40)

    constraints = arrayOf(
      0, 1, 10,
      0, 2, 50,
    )
    testColumnsSizeCalculator(constraints, 50, setOf(), arrayOf(0, 10, 50), 50)
    testColumnsSizeCalculator(constraints, 50, setOf(0), arrayOf(0, 50, 50), 50)
    testColumnsSizeCalculator(constraints, 50, setOf(1), arrayOf(0, 10, 50), 50)
    testColumnsSizeCalculator(constraints, 50, setOf(0, 1), arrayOf(0, 30, 50), 50)


    constraints = arrayOf(
      0, 1, 10,
      1, 1, 10,
      2, 1, 10,
      0, 3, 90,
    )
    testColumnsSizeCalculator(constraints, 90, setOf(), arrayOf(0, 10, 20, 90), 90)
    testColumnsSizeCalculator(constraints, 90, setOf(0), arrayOf(0, 70, 80, 90), 90)
    testColumnsSizeCalculator(constraints, 90, setOf(1), arrayOf(0, 10, 80, 90), 90)
    testColumnsSizeCalculator(constraints, 90, setOf(2), arrayOf(0, 10, 20, 90), 90)
    testColumnsSizeCalculator(constraints, 90, setOf(0, 1), arrayOf(0, 40, 80, 90), 90)
    testColumnsSizeCalculator(constraints, 90, setOf(0, 2), arrayOf(0, 40, 50, 90), 90)
    testColumnsSizeCalculator(constraints, 90, setOf(0, 1, 2), arrayOf(0, 30, 60, 90), 90)

    constraints = arrayOf(
      0, 1, 10,
      1, 1, 10,
      2, 1, 10,
      0, 2, 40,
      1, 2, 30,
    )
    testColumnsSizeCalculator(constraints, 50, setOf(), arrayOf(0, 10, 40, 50), 50)
    testColumnsSizeCalculator(constraints, 50, setOf(0), arrayOf(0, 20, 40, 50), 50)
    testColumnsSizeCalculator(constraints, 50, setOf(1), arrayOf(0, 10, 40, 50), 50)
    testColumnsSizeCalculator(constraints, 50, setOf(2), arrayOf(0, 10, 40, 50), 50)
    testColumnsSizeCalculator(constraints, 50, setOf(0, 1), arrayOf(0, 15, 40, 50), 50) // expected (0, 20, 40, 50), can be fixed later
    testColumnsSizeCalculator(constraints, 50, setOf(0, 2), arrayOf(0, 15, 40, 50), 50) // expected (0, 20, 40, 50), can be fixed later
    testColumnsSizeCalculator(constraints, 50, setOf(1, 2), arrayOf(0, 10, 40, 50), 50)
    testColumnsSizeCalculator(constraints, 50, setOf(0, 1, 2), arrayOf(0, 13, 40, 50), 50) // expected (0, 20, 40, 50), can be fixed later
  }

  /**
   * @param constraints triples like (x, width) and preferredSize
   */
  private fun testColumnsSizeCalculator(constraints: Array<Int>, width: Int, resizableColumns: Set<Int>, expectedCoords: Array<Int>,
                                        expectedPreferredSize: Int) {
    assertEquals(constraints.size % 3, 0)

    val calculator = ColumnsSizeCalculator()
    for (i in constraints.indices step 3) {
      calculator.addConstraint(constraints[i], constraints[i + 1], constraints[i + 2])
    }

    val calculatedCoords = calculator.calculateCoords(width, resizableColumns)
    val message = "Constraints: ${constraints.joinToString()}, width: $width, " +
                  "resizableColumns: ${resizableColumns.joinToString()}, expectedCoords: ${expectedCoords.joinToString()}"

    assertTrue("$message, calculatedCoords: ${calculatedCoords.joinToString()}", expectedCoords.contentEquals(calculatedCoords))

    val preferredSize = calculator.calculatePreferredSize()
    assertEquals("$message, preferredSize: $preferredSize", expectedPreferredSize, preferredSize)
  }
}