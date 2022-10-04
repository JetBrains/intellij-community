// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nostradamus

import com.intellij.testFramework.TestSorter
import org.jetbrains.annotations.TestOnly
import java.util.function.ToIntFunction

class NostradamusTestCaseSorter : TestSorter {

  private val sortedClassesProvider: (List<Class<*>>) -> List<Class<*>>

  companion object {
    fun getRanker(rankedClasses: Map<Class<*>, Int>): ToIntFunction<in Class<*>> {
      return ToIntFunction { currentClass ->
        val rank = rankedClasses[currentClass]
        requireNotNull(rank) { "Rank for class ${currentClass.name} isn't specified" }
        rank
      }
    }
  }

  constructor() {
    this.sortedClassesProvider = { unsortedClasses: List<Class<*>> -> getPrioritizedClasses(unsortedClasses) }
  }

  @TestOnly
  constructor(sortedClassesProvider: (List<Class<*>>) -> List<Class<*>>) {
    this.sortedClassesProvider = sortedClassesProvider
  }

  private fun getPrioritizedClasses(unsortedClasses: List<Class<*>>): List<Class<*>> {
    val sortedClasses: List<Class<*>> = NostradamusClient().getSortedClasses(unsortedClasses)
    return sortedClasses
  }

  private fun validateCollectionsEquality(firstItems: List<Class<*>>, secondItems: List<Class<*>>): Unit {
    val firstSet = firstItems.toSet()
    val secondSet = secondItems.toSet()

    val firstDiff = firstSet.minus(secondSet)
    val secondDiff = secondSet.minus(firstDiff)

    var message = ""

    if (firstDiff.isNotEmpty() || secondDiff.isNotEmpty()) {
      message += """
        Collections are different:
        First:
        ${firstItems.joinToString(separator = System.lineSeparator()) { it.toString() }}
        
        Second:
        ${secondItems.joinToString(separator = System.lineSeparator()) { it.toString() }}
        
        Diff first vs second:
        ${firstDiff.joinToString(separator = System.lineSeparator()) { it.toString() }}
        
        Diff second vs first:
        ${secondDiff.joinToString(separator = System.lineSeparator()) { it.toString() }}
      """.trimIndent()
    }
  }

  override fun sorted(unsortedClasses: MutableList<Class<*>>, ranker: ToIntFunction<in Class<*>>): List<Class<*>> {
    val sortedClasses = sortedClassesProvider(unsortedClasses)

    require(sortedClasses.size == unsortedClasses.size) {
      """
        Size of sorted classes ${sortedClasses.size}, unsorted classes ${unsortedClasses.size}. But they should be equal.
      """
    }

    validateCollectionsEquality(unsortedClasses, sortedClasses)

    var rank = 1
    // for faster lookup convert sorted list of classes to map with ranks
    val rankedClasses: Map<Class<*>, Int> = sortedClasses.associateWith { rank++ }

    return unsortedClasses.sortedWith(
      Comparator.comparingInt(ranker).thenComparingInt(getRanker(rankedClasses))
    )
  }
}