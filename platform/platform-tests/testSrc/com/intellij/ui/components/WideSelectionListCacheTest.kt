// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.ClientProperty
import com.intellij.ui.speedSearch.FilteringListModel
import org.junit.Test
import javax.swing.DefaultListModel
import javax.swing.ListModel
import kotlin.test.assertEquals

class WideSelectionListCacheTest {

  @Test
  fun testNoOptimizationDefaultModel() {
    val list = JBList((0..100).toList())
    val model = list.model as DefaultListModel<Int>

    updateAndCheckCache(list, emptySet())
    model.remove(10)
    updateAndCheckCache(list, emptySet())
    model.addElement(101)
    updateAndCheckCache(list, emptySet())
  }

  @Test
  fun testOptimizationDefaultModel() {
    val list = JBList((0..100).toList())
    val model = list.model as DefaultListModel<Int>

    ClientProperty.put(list, JBList.IMMUTABLE_MODEL_AND_RENDERER, true)

    updateAndCheckCacheEqualsModel(list)
    model.remove(10)
    updateAndCheckCacheEqualsModel(list)
    model.addElement(101)
    updateAndCheckCacheEqualsModel(list)

    list.model = DefaultListModel()
    assertEquals(0, list.cachedItems.size)
  }

  @Test
  fun testFilteringModel() {
    val defaultModel = DefaultListModel<Int>()
    defaultModel.addAll((0..100).toList())
    val filteringModel = FilteringListModel(defaultModel)
    val list = JBList(filteringModel)

    ClientProperty.put(list, JBList.IMMUTABLE_MODEL_AND_RENDERER, true)

    filteringModel.setFilter { it <= 50 }
    updateAndCheckCache(list, (0..50).toSet())
    filteringModel.setFilter { it <= 60 }
    updateAndCheckCache(list, (0..60).toSet())
    filteringModel.setFilter(null)
    updateAndCheckCache(list, (0..100).toSet())
    filteringModel.setFilter { it <= 50 }
    updateAndCheckCache(list, (0..100).toSet())

    filteringModel.setFilter(null)
    defaultModel.remove(49)
    defaultModel.remove(50)
    defaultModel.addElement(49) // remove doesn't purge the cache
    val no50 = defaultModel.allItems
    assertEquals(no50, list.cachedItems)
    defaultModel.addElement(999)
    assertEquals(no50, list.cachedItems)
    updateAndCheckCacheEqualsModel(list)
  }

  private val ListModel<Int>.allItems: Set<Int>
    get() = checkedToSet((0..<size).map { getElementAt(it) })

  private val JBList<Int>.cachedItems: Set<Int>
    get() = checkedToSet((ui as WideSelectionListUI).cacheSupport.preferredSizeCache.keys)

  private fun checkedToSet(list: Collection<Any>): Set<Int> {
    val result = list.filterIsInstance<Int>().toSet()
    assertEquals(result.size, list.size)
    return result
  }

  private fun updateAndCheckCache(list: JBList<Int>, items: Set<Any>) {
    val ui = list.ui as WideSelectionListUI
    ui.updateLayoutState()

    assertEquals(list.cachedItems, items.toSet())
  }

  private fun updateAndCheckCacheEqualsModel(list: JBList<Int>) {
    updateAndCheckCache(list, list.model.allItems)
  }
}
