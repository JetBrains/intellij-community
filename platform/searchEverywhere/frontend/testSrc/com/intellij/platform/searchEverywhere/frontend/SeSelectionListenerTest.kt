// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SeItemDataImpl
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultJBList
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListModel
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListMoreRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import javax.swing.DefaultListSelectionModel

/**
 * Unit tests for [SeSelectionListener], which implements the auto-selection policy of the split
 * Search Everywhere result list (see `SePopupContentPane.autoSelectIndex`). Every branch of
 * [SeSelectionListener.getIndexToSelect] is covered deterministically, plus
 * [SeSelectionListener.saveSelectionState]/[SeSelectionListener.getSelectionState] and the supporting
 * [SeResultJBList] contract that the `SePopupContentPane` guard relies on.
 */
class SeSelectionListenerTest {
  private val maxVisibleRowCountLarge = 10

  private fun newModel(): SeResultListModel =
    SeResultListModel(SeSearchStatePublisher()) { DefaultListSelectionModel() }

  private fun newList(model: SeResultListModel): SeResultJBList<SeResultListRow> = SeResultJBList(model)

  /**
   * Mocks [SeItemDataImpl] (the only [com.intellij.platform.searchEverywhere.SeItemData] impl). Test items
   * must be `SeItemDataImpl` because `SeItemData.contentEquals` casts to it. By default `contentEquals`
   * returns `false`; use [markEqual] to declare a content match.
   */
  private fun item(): SeItemDataImpl = Mockito.mock(SeItemDataImpl::class.java)

  /** Declares `item.contentEquals(saved) == true`, modeling that `item` has the saved presentation. */
  private fun markEqual(item: SeItemDataImpl, saved: SeItemDataImpl) {
    Mockito.`when`(item.contentEquals(saved)).thenReturn(true)
  }

  private fun SeResultListModel.addItems(vararg items: SeItemDataImpl) {
    items.forEach { addElement(SeResultListItemRow(it)) }
  }

  private fun SeResultListModel.addMoreRow() = addElement(SeResultListMoreRow)

  // region getIndexToSelect

  @Test
  fun returnsZero_whenNoInitialSelectionState() {
    val model = newModel()
    model.addItems(item(), item())
    val listener = SeSelectionListener(null, newList(model), model)

    assertEquals(0, listener.getIndexToSelect(maxVisibleRowCountLarge, "anything", isInitialSearchPattern = true, isEndEvent = false))
  }

  @Test
  fun returnsZero_whenSavedItemAlreadyAtIndexZero() {
    val model = newModel()
    val saved = item()
    val first = item().also { markEqual(it, saved) }
    model.addItems(first, item())
    val listener = SeSelectionListener(SeSelectionState("p", saved), newList(model), model)

    assertEquals(0, listener.getIndexToSelect(maxVisibleRowCountLarge, "p", isInitialSearchPattern = false, isEndEvent = false))
  }

  @Test
  fun returnsMinusOne_whenUserAlreadyMovedSelectionBelowFirst() {
    val model = newModel()
    val saved = item()
    model.addItems(item(), item(), item()) // none equal to saved
    val list = newList(model)
    list.selectedIndex = 2
    val listener = SeSelectionListener(SeSelectionState("p", saved), list, model)

    assertEquals(-1, listener.getIndexToSelect(maxVisibleRowCountLarge, "p", isInitialSearchPattern = false, isEndEvent = false))
  }

  @Test
  fun returnsMinusOne_whenModelEmpty() {
    val model = newModel()
    val listener = SeSelectionListener(SeSelectionState("p", item()), newList(model), model)

    assertEquals(-1, listener.getIndexToSelect(maxVisibleRowCountLarge, "p", isInitialSearchPattern = false, isEndEvent = false))
  }

  @Test
  fun returnsMinusOne_whenModelHasOnlyMoreRow() {
    // The loading row must not count as a real item: effectiveModelSize == 0.
    val model = newModel()
    model.addMoreRow()
    val listener = SeSelectionListener(SeSelectionState("p", item()), newList(model), model)

    assertEquals(-1, listener.getIndexToSelect(maxVisibleRowCountLarge, "p", isInitialSearchPattern = false, isEndEvent = false))
  }

  @Test
  fun clearsState_andReturnsZero_onEndEvent() {
    val model = newModel()
    model.addItems(item(), item()) // none equal to saved
    val listener = SeSelectionListener(SeSelectionState("p", item()), newList(model), model)

    assertEquals(0, listener.getIndexToSelect(maxVisibleRowCountLarge, "p", isInitialSearchPattern = false, isEndEvent = true))
    assertNull(listener.getSelectionState())
  }

  @Test
  fun clearsState_whenInitialPatternAndPatternChanged() {
    val model = newModel()
    model.addItems(item(), item()) // none equal to saved
    val listener = SeSelectionListener(SeSelectionState("old", item()), newList(model), model)

    assertEquals(0, listener.getIndexToSelect(maxVisibleRowCountLarge, "new", isInitialSearchPattern = true, isEndEvent = false))
    assertNull(listener.getSelectionState())
  }

  @Test
  fun keepsState_whenNotInitialPattern_evenIfPatternChanged() {
    val model = newModel()
    model.addItems(item()) // single item, not equal to saved
    val listener = SeSelectionListener(SeSelectionState("old", item()), newList(model), model)

    assertEquals(0, listener.getIndexToSelect(maxVisibleRowCountLarge, "new", isInitialSearchPattern = false, isEndEvent = false))
    assertNotNull(listener.getSelectionState())
  }

  @Test
  fun returnsMatchingIndex_whenSavedItemFoundWithinVisibleRange() {
    val model = newModel()
    val saved = item()
    val match = item().also { markEqual(it, saved) }
    model.addItems(item(), item(), match, item()) // match at index 2; model[0] differs
    val listener = SeSelectionListener(SeSelectionState("p", saved), newList(model), model)

    assertEquals(2, listener.getIndexToSelect(maxVisibleRowCountLarge, "p", isInitialSearchPattern = false, isEndEvent = false))
  }

  @Test
  fun returnsZero_andClearsState_whenNoMatch_andRangeExhausted() {
    val model = newModel()
    model.addItems(item(), item(), item()) // none equal to saved
    val listener = SeSelectionListener(SeSelectionState("p", item()), newList(model), model)

    // effectiveModelSize - 1 (2) >= maxVisibleRowCount - 1 (2): the whole visible window was scanned.
    assertEquals(0, listener.getIndexToSelect(maxVisibleRowCount = 3, currentPattern = "p", isInitialSearchPattern = false, isEndEvent = false))
    assertNull(listener.getSelectionState())
  }

  @Test
  fun returnsZero_andKeepsState_whenNoMatch_andRangeNotExhausted() {
    val model = newModel()
    model.addItems(item(), item()) // none equal to saved
    val listener = SeSelectionListener(SeSelectionState("p", item()), newList(model), model)

    // Visible window (maxVisibleRowCount = 10) larger than the model: more results may still match later.
    assertEquals(0, listener.getIndexToSelect(maxVisibleRowCountLarge, "p", isInitialSearchPattern = false, isEndEvent = false))
    assertNotNull(listener.getSelectionState())
  }

  @Test
  fun searchRangeBoundedByMaxVisibleRowCount() {
    val model = newModel()
    val saved = item()
    val outOfRangeMatch = item().also { markEqual(it, saved) }
    model.addItems(item(), item(), item(), item(), outOfRangeMatch) // match at index 4
    val listener = SeSelectionListener(SeSelectionState("p", saved), newList(model), model)

    // maxVisibleRowCount = 3 limits the scan to indices 0..2, so the match at index 4 is not found.
    assertEquals(0, listener.getIndexToSelect(maxVisibleRowCount = 3, currentPattern = "p", isInitialSearchPattern = false, isEndEvent = false))
    assertNull(listener.getSelectionState())
  }

  @Test
  fun matchAtFirstVisibleIndexWins() {
    val model = newModel()
    val saved = item()
    val firstMatch = item().also { markEqual(it, saved) }
    val laterMatch = item().also { markEqual(it, saved) }
    model.addItems(item(), firstMatch, item(), laterMatch) // model[0] differs; matches at 1 and 3
    val listener = SeSelectionListener(SeSelectionState("p", saved), newList(model), model)

    assertEquals(1, listener.getIndexToSelect(maxVisibleRowCountLarge, "p", isInitialSearchPattern = false, isEndEvent = false))
  }

  // endregion

  // region saveSelectionState / getSelectionState

  @Test
  fun saveSelectionState_noOp_whenSelectedIndexIsMinusOne() {
    val model = newModel()
    model.addItems(item())
    val list = newList(model)
    list.selectedIndex = -1
    val initial = SeSelectionState("init", item())
    val listener = SeSelectionListener(initial, list, model)

    listener.saveSelectionState("foo")

    assertSame(initial, listener.getSelectionState())
  }

  @Test
  fun saveSelectionState_savesItemAndPattern_whenItemRowSelected() {
    val model = newModel()
    val selected = item()
    model.addItems(item(), selected)
    val list = newList(model)
    list.selectedIndex = 1
    val listener = SeSelectionListener(null, list, model)

    listener.saveSelectionState("foo")

    val state = listener.getSelectionState()
    assertNotNull(state)
    assertEquals("foo", state!!.pattern)
    assertSame(selected, state.selectedItem)
  }

  @Test
  fun saveSelectionState_noOp_whenSelectedRowIsMoreRow() {
    val model = newModel()
    model.addItems(item())
    model.addMoreRow()
    val list = newList(model)
    list.selectedIndex = 1 // the SeResultListMoreRow
    val initial = SeSelectionState("init", item())
    val listener = SeSelectionListener(initial, list, model)

    listener.saveSelectionState("foo")

    assertSame(initial, listener.getSelectionState())
  }

  @Test
  fun getSelectionState_returnsInitial_whenNothingSaved() {
    val model = newModel()
    val initial = SeSelectionState("init", item())
    val listener = SeSelectionListener(initial, newList(model), model)

    assertSame(initial, listener.getSelectionState())
  }

  // endregion

  // region SeResultJBList contract

  @Test
  fun autoSelectIndex_setsSelectedIndex_andResetsFlag() {
    val model = newModel()
    model.addItems(item(), item(), item())
    val list = newList(model)

    list.autoSelectIndex(2)

    assertEquals(2, list.selectedIndex)
    assertFalse(list.isProgrammaticSelectionChange)
  }

  @Test
  fun withProgrammaticSelectionChange_setsFlagInsideBlock_andRestoresAfter() {
    val list = newList(newModel().apply { addItems(item(), item()) })
    assertFalse(list.isProgrammaticSelectionChange)

    var flagInsideBlock = false
    list.withProgrammaticSelectionChange {
      flagInsideBlock = list.isProgrammaticSelectionChange
      // A nested call keeps the flag set and restores the previous (still set) value afterwards.
      list.withProgrammaticSelectionChange { assertTrue(list.isProgrammaticSelectionChange) }
      assertTrue(list.isProgrammaticSelectionChange)
    }

    assertTrue(flagInsideBlock)
    assertFalse(list.isProgrammaticSelectionChange)
  }

  @Test
  fun saveGuard_ignoresProgrammaticSelectionChange_butSavesUserSelectionChange() {
    val model = newModel()
    val userPicked = item()
    model.addItems(item(), userPicked) // index 0 = some item, index 1 = the item the user picks
    val list = newList(model)
    val initial = SeSelectionState("init", item())
    val listener = SeSelectionListener(initial, list, model)

    list.addListSelectionListener {
      if (!list.isProgrammaticSelectionChange) listener.saveSelectionState("pattern")
    }

    // Programmatic selection change (model mutation / auto-select) must be ignored.
    list.withProgrammaticSelectionChange { list.selectedIndex = 0 }
    assertSame(initial, listener.getSelectionState())

    // A genuine user selection change is persisted.
    list.selectedIndex = 1
    val saved = listener.getSelectionState()
    assertNotNull(saved)
    assertSame(userPicked, saved!!.selectedItem)
  }

  @Test
  fun getEffectiveModelSize_excludesTrailingMoreRow() {
    val empty = newModel()
    assertEquals(0, newList(empty).getEffectiveModelSize())

    val single = newModel().apply { addItems(item()) }
    assertEquals(1, newList(single).getEffectiveModelSize())

    val itemPlusMore = newModel().apply { addItems(item()); addMoreRow() }
    assertEquals(1, newList(itemPlusMore).getEffectiveModelSize())

    val twoItems = newModel().apply { addItems(item(), item()) }
    assertEquals(2, newList(twoItems).getEffectiveModelSize())

    val onlyMore = newModel().apply { addMoreRow() }
    assertEquals(0, newList(onlyMore).getEffectiveModelSize())
  }

  // endregion
}
