// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import org.junit.jupiter.api.Test
import java.util.function.Supplier
import kotlin.test.assertEquals

@RunInEdt
@TestApplication
class ActionPopupTest {
  @Test
  fun testMnemonicsInActonPopup() {
    val booleans = listOf(false, true)
    for (showNumbers in booleans) {
      for (useAlphaAsNumbers in booleans) {
        for (honorActionMnemonics in booleans) {
          val options = createOptions(showNumbers, useAlphaAsNumbers, honorActionMnemonics)
          testActionPresentation(TextWithMnemonic.fromPlainText("Text With Mnemonic", 'W'), "Text With Mnemonic", options)
          testActionPresentation(TextWithMnemonic.fromPlainText("Text With &Mnemonic", 'W'), "Text With &Mnemonic", options)
          testActionPresentation(TextWithMnemonic.fromPlainText("Text With _Mnemonic", 'W'), "Text With _Mnemonic", options)
          testActionPresentation(TextWithMnemonic.fromPlainText("Text Without Mnemonic"), "Text Without Mnemonic", options)
          testActionPresentation(TextWithMnemonic.fromPlainText("Text_Without_Mnemonic"), "Text_Without_Mnemonic", options)
          testActionPresentation(TextWithMnemonic.fromPlainText("Text &With Mnemonic"), "Text &With Mnemonic", options)
          testActionPresentation(TextWithMnemonic.fromPlainText("Text &&__ Mnemonic"), "Text &&__ Mnemonic", options)
        }
      }
    }
  }

  private fun testActionPresentation(textWithMnemonic: TextWithMnemonic, expectedText: String, options: ActionPopupOptions) {
    val group = DefaultActionGroup()
    group.add(DumbAction(textWithMnemonic))

    val presentationFactory = PresentationFactory()
    val dataContext: DataContext = SimpleDataContext.EMPTY_CONTEXT
    val step = ActionPopupStep.createActionsStep(null, group, dataContext, ActionPlaces.PROJECT_WIDGET_POPUP, presentationFactory,
                                                 Supplier { dataContext }, options)
    val popup = JBPopupFactory.getInstance().createListPopup(step) as ListPopupImpl
    val renderer = popup.list.cellRenderer.getListCellRendererComponent(popup.list, popup.list.model.getElementAt(0), 0, true, false) as GroupedElementsRenderer.MyComponent
    val text = (renderer.renderer as PopupListElementRenderer<*>).textInTests

    assertEquals(expectedText, text, "Options: ${options.showNumbers()}, ${options.useAlphaAsNumbers()}, ${options.honorActionMnemonics()}")
  }
}

private class DumbAction(textWithMnemonic: TextWithMnemonic) : AnAction() {
  init {
    templatePresentation.setTextWithMnemonic { textWithMnemonic }
  }

  override fun actionPerformed(e: AnActionEvent) {
  }
}

private fun createOptions(showNumbers: Boolean, useAlphaAsNumbers: Boolean, honorActionMnemonics: Boolean): ActionPopupOptions {
  return ActionPopupOptions.forStepAndItems(showNumbers, useAlphaAsNumbers, true, honorActionMnemonics, false, null, 0)
}
