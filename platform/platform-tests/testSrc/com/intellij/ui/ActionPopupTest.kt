// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
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
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNotNull
import java.awt.Component
import java.awt.Container
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
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

  @Test
  fun testNonJComponentDataContextPopupLocation() {
    val component: Component = HeadlessRootPaneContainer()
    val context = IdeUiService.getInstance().createUiDataContext(component)
    val point = JBPopupFactory.getInstance().guessBestPopupLocation(context)
    assertNotNull(point)
  }

  @Test
  fun testListRendererWithoutIconLabel() {
    val group = DefaultActionGroup()
    group.add(object : AnAction("text") {
      override fun actionPerformed(e: AnActionEvent) {}
    })
    val step = ActionPopupStep.createActionsStep(null, group, SimpleDataContext.EMPTY_CONTEXT, ActionPlaces.PROJECT_WIDGET_POPUP,
                                                 PresentationFactory(), Supplier { SimpleDataContext.EMPTY_CONTEXT },
                                                 createOptions(false, false, false))
    val popup = object : ListPopupImpl(null, step) {
      override fun getListElementRenderer() = object : PopupListElementRenderer<Any>(this) {
        override fun createItemComponent(): JComponent? {
          // Intentionally not creating myIconLabel here, similar to com.intellij.openapi.vcs.ui.PopupListElementRendererWithIcon
          myTextLabel = ErrorLabel()
          return layoutComponent(myTextLabel)
        }
      }
    }
    assertDoesNotThrow {
      popup.list.cellRenderer.getListCellRendererComponent(popup.list, popup.list.model.getElementAt(0), 0, true, false)
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

private class HeadlessRootPaneContainer : Component(), RootPaneContainer {
  private val rootPane: JRootPane = JRootPane()
  override fun getRootPane(): JRootPane = rootPane

  override fun setContentPane(contentPane: Container?) = Unit
  override fun getContentPane(): Container? = rootPane.getContentPane()

  override fun setLayeredPane(layeredPane: JLayeredPane?) = Unit
  override fun getLayeredPane(): JLayeredPane? = rootPane.getLayeredPane()

  override fun setGlassPane(glassPane: Component?) = Unit
  override fun getGlassPane(): Component? = rootPane.glassPane
}

