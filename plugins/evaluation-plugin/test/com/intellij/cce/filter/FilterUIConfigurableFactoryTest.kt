package com.intellij.cce.filter

import com.intellij.cce.dialog.configurable.FilterUIConfigurableFactory
import com.intellij.cce.workspace.ConfigFactory
import com.intellij.ui.dsl.builder.panel
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FilterUIConfigurableFactoryTest {
  companion object {
    private const val TEST_FILTER_ID = "testFilter"
  }

  @Test
  fun `all filters that have UI could build view`() = doTest { factory ->
    EvaluationFilterManager.getAllFilters().forEach {
      if (it.hasUI) assertNotNull(factory.build(it.id))
    }
  }

  @Test
  fun `exception if filter with UI couldn't build view`() {
    val testFilterConfiguration = TestFilterConfiguration()
    try {
      EvaluationFilterManager.registerFilter(testFilterConfiguration)
      doTest { factory ->
        assertThrows<IllegalStateException> { factory.build(TEST_FILTER_ID) }
      }
    }
    finally {
      EvaluationFilterManager.unregisterFilter(testFilterConfiguration)
      assertNull(EvaluationFilterManager.getConfigurationById(TEST_FILTER_ID))
    }
  }

  @Test
  fun `exception if unknown filter`() = doTest { factory ->
    assertThrows<IllegalArgumentException> { factory.build("unknownFilter") }
  }

  private fun doTest(body: (FilterUIConfigurableFactory) -> Unit) {
    panel {
      val factory = FilterUIConfigurableFactory(ConfigFactory.defaultConfig(), this)
      body(factory)
    }
  }

  private class TestFilterConfiguration : EvaluationFilterConfiguration {
    override val id: String = TEST_FILTER_ID
    override val description: String = "Should be used only in tests"
    override val hasUI: Boolean = true
    override fun isLanguageSupported(languageName: String): Boolean = true
    override fun buildFromJson(json: Any?): EvaluationFilter = EvaluationFilter.ACCEPT_ALL
    override fun defaultFilter(): EvaluationFilter = EvaluationFilter.ACCEPT_ALL
  }
}