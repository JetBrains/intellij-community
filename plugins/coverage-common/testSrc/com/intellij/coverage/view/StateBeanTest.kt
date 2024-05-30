package com.intellij.coverage.view

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.*
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
    val names = element.children.map { it.getAttribute("name").value }.sorted().joinToString("\n")
    assertEquals("""
      flattenPackages
      hideFullyCovered
      myAscendingOrder
      myAutoScrollFromSource
      myAutoScrollToSource
      myColumnSize
      mySortingColumn
      showOnlyModified
    """.trimIndent(), names)
  }
}
