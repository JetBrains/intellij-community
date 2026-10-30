// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.openapi.application.UI
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class DarculaJBPopupComboPopupTest {
  @Test
  fun `the selected item is read with the renderer set after the popup was created`(): Unit = timeoutRunBlocking {
    val name = withContext(Dispatchers.UI) {
      val comboBox = ComboBox(arrayOf(Theme("Islands Dark"), Theme("Islands Light")))
      comboBox.isSwingPopup = false
      comboBox.setUI(DarculaComboBoxUI())
      comboBox.renderer = textListCellRenderer { it.title }

      comboBox.accessibleContext.accessibleSelection.getAccessibleSelection(0)?.accessibleContext?.accessibleName
    }

    assertThat(name).isEqualTo("Islands Dark")
  }

  private data class Theme(val title: String)
}
