// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.codeInsight.editorActions.TextBlockTransferable
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

@TestApplication
internal class CopyPasteManagerPluginUnloadTest {
  private val manager = RecordingCopyPasteManager()

  @Test
  fun `the top item keeps its text and loses the stale data`() {
    manager.setContents(TextBlockTransferable("a", listOf(StaleData()), null))

    manager.forgetDataOf(::isStale)

    val top = manager.systemContent
    assertThat(top).isInstanceOf(TextBlockTransferable::class.java)
    assertThat(top!!.getTransferData(DataFlavor.stringFlavor)).isEqualTo("a")
    assertThat(top.isDataFlavorSupported(StaleData.FLAVOR)).isFalse()
    assertThat(manager.allContents[0]).isSameAs(top)
  }

  @Test
  fun `a history item is replaced in place and a clean top item stays the same instance`() {
    manager.setContents(TextBlockTransferable("b", listOf(StaleData()), null))
    val top = TextBlockTransferable("a", emptyList(), null)
    manager.setContents(top)

    manager.forgetDataOf(::isStale)

    assertThat(manager.systemContent).isSameAs(top)
    assertThat(manager.allContents[0]).isSameAs(top)
    val replaced = manager.allContents[1]
    assertThat(replaced.getTransferData(DataFlavor.stringFlavor)).isEqualTo("b")
    assertThat(replaced.isDataFlavorSupported(StaleData.FLAVOR)).isFalse()
  }

  @Test
  fun `a transferable of a stale class leaves the history`() {
    val next = StringSelection("a")
    manager.setContents(next)
    manager.setContents(StaleTransferable("b"))

    manager.forgetDataOf(::isStale)

    assertThat(manager.systemContent).isSameAs(next)
    assertThat(manager.allContents).containsExactly(next)
  }
}

private fun isStale(aClass: Class<*>): Boolean = aClass == StaleData::class.java || aClass == StaleTransferable::class.java

private class StaleData : TextBlockTransferableData {
  companion object {
    val FLAVOR = DataFlavor(StaleData::class.java, "stale")
  }

  override fun getFlavor(): DataFlavor = FLAVOR
}

private class StaleTransferable(text: String) : StringSelection(text)

private class RecordingCopyPasteManager : CopyPasteManagerWithHistory() {
  var systemContent: Transferable? = null

  override fun doSetSystemClipboardContent(content: Transferable) {
    systemContent = content
  }

  override fun areDataFlavorsAvailable(vararg flavors: DataFlavor): Boolean = false

  override fun getContents(): Transferable? = null

  override fun <T : Any?> getContents(flavor: DataFlavor): T? = null

  override fun isSystemSelectionSupported(): Boolean = false

  override fun getSystemSelectionContents(): Transferable? = null

  override fun setSystemSelectionContents(content: Transferable) {
  }
}
