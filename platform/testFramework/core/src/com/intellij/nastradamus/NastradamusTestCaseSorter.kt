// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus

import com.intellij.testFramework.TestSorter
import org.jetbrains.annotations.TestOnly
import java.util.function.ToIntFunction

class NastradamusTestCaseSorter : TestSorter {

  private val rankedClassesProvider: (List<Class<*>>) -> Map<Class<*>, Int>

  companion object {
    fun getRanker(rankedClasses: Map<Class<*>, Int>): ToIntFunction<in Class<*>> {
      return ToIntFunction { currentClass ->
        val rank = rankedClasses[currentClass]
        requireNotNull(rank) { "Rank for class ${currentClass.name} isn't specified. Probably sorting didn't return anything for class" }
        require(rank >= 0) { "Rank for class ${currentClass.name} is negative. Probably sorting didn't return anything for class" }
        rank
      }
    }
  }

  constructor() {
    this.rankedClassesProvider = { unsortedClasses: List<Class<*>> -> getRankedClasses(unsortedClasses) }
  }

  @TestOnly
  constructor(rankedClassesProvider: (List<Class<*>>) -> Map<Class<*>, Int>) {
    this.rankedClassesProvider = rankedClassesProvider
  }

  private fun getRankedClasses(unsortedClasses: List<Class<*>>): Map<Class<*>, Int> {
    return NastradamusClient().getRankedClasses(unsortedClasses)
  }

  private fun validateCollectionsEquality(firstItems: List<Class<*>>, rankedItems: Map<Class<*>, Int>): Unit {
    val firstSet = firstItems.toSet()
    val secondSet = rankedItems.keys

    val firstDiff = firstSet.minus(secondSet)
    val secondDiff = secondSet.minus(firstDiff)

    var message = ""

    if (firstDiff.isNotEmpty() || secondDiff.isNotEmpty()) {
      message += """
        Collections are different:
        First:
        ${firstItems.joinToString(separator = System.lineSeparator()) { it.toString() }}
        
        Second:
        ${secondSet.joinToString(separator = System.lineSeparator()) { it.toString() }}
        
        Diff first vs second:
        ${firstDiff.joinToString(separator = System.lineSeparator()) { it.toString() }}
        
        Diff second vs first:
        ${secondDiff.joinToString(separator = System.lineSeparator()) { it.toString() }}
      """.trimIndent()
    }
  }

  override fun sorted(unsortedClasses: MutableList<Class<*>>, ranker: ToIntFunction<in Class<*>>): List<Class<*>> {
    val rankedClasses = rankedClassesProvider(unsortedClasses)

    require(rankedClasses.size == unsortedClasses.size) {
      """
        Not equal sizes of sorted classes ${rankedClasses.size} and unsorted classes ${unsortedClasses.size}.
      """
    }

    validateCollectionsEquality(unsortedClasses, rankedClasses)

    return unsortedClasses.sortedWith(
      Comparator.comparingInt(ranker).thenComparingInt(getRanker(rankedClasses))
    )
  }
}