// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.TestCaseLoader
import com.intellij.nastradamus.NastradamusTestCaseSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class TestClassesSortingTest {

  private class Debug1Test
  private class Debug2Test
  private class Debug3Test

  private class DebugAnother1Test
  private class DebugAnother2Test
  private class DebugAnother3Test

  private class OneMore1Test
  private class OneMore2Test

  private val rankedClasses = mapOf<Class<*>, Int>(
    DebugAnother2Test::class.java to 1,
    Debug1Test::class.java to 2,
    OneMore1Test::class.java to 3,
    DebugAnother1Test::class.java to 4,
    OneMore2Test::class.java to 5,
    DebugAnother3Test::class.java to 6,
    Debug2Test::class.java to 7,
    Debug3Test::class.java to 8,
  )

  @Test
  fun checkGeneralSortingLogic() {
    val shuffledSourceClasses = rankedClasses
      .map { it.key }
      .shuffled()
      .toMutableList()

    val sortedClasses: List<Class<*>> = NastradamusTestCaseSorter { _ -> rankedClasses }
      .sorted(unsortedClasses = shuffledSourceClasses, ranker = TestCaseLoader::getRank)

    val failureMessage =
      """
      Expected order of classes:
      ${rankedClasses.entries.joinToString(separator = System.lineSeparator()) { it.toString() }}
      
      Sorted order of classes:
      ${sortedClasses.joinToString(separator = System.lineSeparator()) { it.toString() }}
    """.trimMargin().trimIndent()

    assertEquals("Size of collection provided ranked classes and sorted classes should be the same",
                 rankedClasses.size, sortedClasses.size)

    sortedClasses.forEachIndexed { index, clazz ->
      val expectedSortedRank = rankedClasses.get(clazz)?.minus(1)
      assertEquals(
        "Sorted $clazz should have position with index $expectedSortedRank in collection, but it's actual position is $index"
          .plus(System.lineSeparator())
          .plus(failureMessage).trimMargin().trimIndent(),
        expectedSortedRank, index
      )
    }
  }

  @Test
  fun checkSortingWithBucketing() {
    // TODO
  }

  // TODO: test on provided ranked classes list size < found classes list size
  // TODO: test on found classes list size < provided ranked classes size
}