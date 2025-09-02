package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.CellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import javax.swing.JComboBox
import kotlin.time.Duration.Companion.seconds


fun Finder.comboBox(@Language("xpath") xpath: String? = null) =
  x(xpath ?: xQuery { byType(JComboBox::class.java) }, JComboBoxUiComponent::class.java)

fun Finder.comboBox(locator: QueryBuilder.() -> String) = x(JComboBoxUiComponent::class.java) { locator() }

fun Finder.accessibleComboBox(locator: QueryBuilder.() -> String = { byType(JComboBox::class.java) }) =
  comboBox(locator).apply {
    replaceCellRendererReader {
      driver.new(AccessibleNameCellRendererReader::class, rdTarget = (it as RefWrapper).getRef().rdTarget)
    }
  }

class JComboBoxUiComponent(data: ComponentData) : UiComponent(data) {
  private var cellRendererReaderSupplier: ((JComboBoxFixtureRef) -> CellRendererReader)? = null
  private val fixture by lazy {
    driver.new(JComboBoxFixtureRef::class, robot, component).apply {
      cellRendererReaderSupplier?.let { replaceCellRendererReader(it(this)) }
    }
  }

  fun selectItem(text: String) {
    should("'$text' item found", 5.seconds, { "'$text' item not found, available items: ${fixture.listValues()}" }) {
      listValues().singleOrNull { it == text } != null
    }
    fixture.select(text)
  }

  fun enterText(text: String): JComboBoxFixtureRef {
    return fixture.enterText(text)
  }

  fun selectItemContains(text: String) {
    if (fixture.listValues().singleOrNull { it.contains(text) } == null) {
      click()
    }
    fixture.select(fixture.listValues().single { it.contains(text) })
  }

  fun listValues() = fixture.listValues()

  fun getSelectedItem() = fixture.selectedText()

  fun replaceCellRendererReader(readerSupplier: (JComboBoxFixtureRef) -> CellRendererReader) {
    cellRendererReaderSupplier = readerSupplier
  }
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JComboBoxTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JComboBoxFixtureRef {
  fun selectedText(): String
  fun listValues(): List<String>
  fun select(text: String)
  fun enterText(text: String): JComboBoxFixtureRef
  fun replaceCellRendererReader(reader: CellRendererReader)
}