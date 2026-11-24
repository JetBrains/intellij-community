// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.util.TypeVisibilityStateHolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The search of another search project applies the type filter of the popup to the legacy filter of that project.
 * [PersistentSearchEverywhereContributorFilter.keepSelectionInMemory] keeps the saved filter of that project.
 */
class SeInMemoryTypeSelectionTest {

  @Test
  fun aSelectionInMemoryKeepsTheSavedState() {
    val saved = SavedState(hidden = mutableSetOf("Kotlin"))
    val filter = filterOf(saved)

    filter.keepSelectionInMemory()
    filter.setSelected("Kotlin", true)
    filter.setSelected("Java", false)

    assertEquals(listOf("Kotlin", "Groovy"), filter.selectedElements, "the search uses the filter of the popup")
    assertEquals(setOf("Kotlin"), saved.hidden, "the saved filter of the project does not change")
  }

  @Test
  fun aSelectionInMemoryStartsAsTheSavedState() {
    val filter = filterOf(SavedState(hidden = mutableSetOf("Groovy")))

    filter.keepSelectionInMemory()

    assertEquals(listOf("Java", "Kotlin"), filter.selectedElements)
  }

  @Test
  fun aSecondCallKeepsTheSelectionInMemory() {
    val filter = filterOf(SavedState(hidden = mutableSetOf()))

    filter.keepSelectionInMemory()
    filter.setSelected("Java", false)
    filter.keepSelectionInMemory()

    assertEquals(listOf("Kotlin", "Groovy"), filter.selectedElements)
  }

  @Test
  fun withoutTheCallTheFilterSavesTheSelection() {
    val saved = SavedState(hidden = mutableSetOf())
    val filter = filterOf(saved)

    filter.setSelected("Java", false)

    assertEquals(setOf("Java"), saved.hidden)
  }

  private fun filterOf(saved: SavedState): PersistentSearchEverywhereContributorFilter<String> =
    PersistentSearchEverywhereContributorFilter(listOf("Java", "Kotlin", "Groovy"), saved, { it }, { null })

  private class SavedState(val hidden: MutableSet<String>) : TypeVisibilityStateHolder<String> {
    override fun setVisible(type: String, value: Boolean) {
      if (value) hidden.remove(type) else hidden.add(type)
    }

    override fun isVisible(type: String): Boolean = type !in hidden
  }
}
