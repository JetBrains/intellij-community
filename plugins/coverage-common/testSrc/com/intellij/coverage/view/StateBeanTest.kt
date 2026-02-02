package com.intellij.coverage.view

import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StateBeanTest {
  @Test
  fun `test has constructor with no arguments`() {
    CoverageViewManager.StateBean()
  }

  @Test
  fun `test field names are stable`() {
    val stateBean = CoverageViewManager.StateBean().apply {
      myColumnSize = listOf(1, 2, 3)
    }
    val element = XmlSerializer.serialize(stateBean)
    val names = element.children.map { element ->
      if (element.name == "option") {
        element.getAttribute("name").value
      } else {
        element.name
      }
    }.sorted().joinToString("\n")
    assertEquals("""
      flattenPackages
      hideFullyCovered
      myAscendingOrder
      myAutoScrollFromSource
      myAutoScrollToSource
      myColumnSize
      mySortingColumn
      showOnlyModified_v2
    """.trimIndent(), names)
  }
}
