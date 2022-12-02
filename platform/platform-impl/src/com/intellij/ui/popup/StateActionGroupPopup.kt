// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.PillBorder
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*

open class StateActionGroupPopup(@NlsContexts.PopupTitle title: String?,
                                 actionGroup: ActionGroup,
                                 dataContext: DataContext,
                                 showNumbers: Boolean,
                                 useAlphaAsNumbers: Boolean,
                                 showDisabledActions: Boolean,
                                 honorActionMnemonics: Boolean,
                                 disposeCallback: Runnable?,
                                 maxRowCount: Int,
                                 preselectActionCondition: Condition<AnAction>?,
                                 actionPlace: String?,
                                 autoSelection: Boolean,
                                 val getState: (AnAction) -> @Nls String?) :
  PopupFactoryImpl.ActionGroupPopup(title, actionGroup, dataContext, showNumbers,
                                    useAlphaAsNumbers,
                                    showDisabledActions, honorActionMnemonics,
                                    disposeCallback, maxRowCount,
                                    preselectActionCondition,
                                    actionPlace, autoSelection) {

  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): WizardPopup {
    return if (step is ListPopupStep<*>) {
      object : ListPopupImpl(project, parent, step, parentValue) {
        override fun getListElementRenderer(): ListCellRenderer<*> {
          return createRenderer(this)
        }
      }
    }
    else super.createPopup(parent, step, parentValue)

  }

  private fun createRenderer(popup: ListPopupImpl): PopupListElementRenderer<Any?> {
    return object : PopupListElementRenderer<Any?>(popup) {
      private var stateLabel: JLabel? = null
      private var stateButton: JComponent? = null
      private var shortcutLabel: JLabel? = null

      override fun createItemComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        val rightPane = JPanel(BorderLayout())
        createLabel()
        panel.add(myTextLabel, BorderLayout.CENTER)
        myTextLabel.border = JBUI.Borders.emptyTop(1)
        myIconBar = createIconBar()

        val bt = createButton()
        rightPane.add(bt, BorderLayout.CENTER)
        stateButton = bt

        val shLabel = JLabel()
        shortcutLabel = shLabel
        shLabel.border = JBUI.Borders.emptyRight(3)
        shLabel.foreground = UIManager.getColor("MenuItem.acceleratorForeground")
        rightPane.add(shLabel, BorderLayout.EAST)
        panel.add(rightPane, BorderLayout.EAST)
        rightPane.border = JBUI.Borders.emptyLeft(5)
        return layoutComponent(panel)
      }

      override fun createLabel() {
        super.createLabel()
        myIconLabel.border = JBUI.Borders.empty(1, 0, 0, JBUI.CurrentTheme.ActionsList.elementIconGap())
      }

      override fun createIconBar(): JComponent {
        return myIconLabel
      }

      private fun createButton(): JComponent {
        val pane = JPanel(MigLayout("ins 0, gap 0")).apply {
          isOpaque = false
        }

        val lb = object : JLabel() {
          override fun getFont(): Font {
            return JBUI.Fonts.miniFont()
          }
        }.apply {
          foreground = JBUI.CurrentTheme.Advertiser.foreground()
        }

        stateLabel = lb
        pane.add(lb, "gapbefore ${JBUI.scale(3)}, gapafter ${JBUI.scale(3)}, ay center")
        pane.border = PillBorder(
          JBUI.CurrentTheme.Advertiser.background(), 1)

        return JPanel(MigLayout("ins 0, gap 0, ay center", "", "push[]push")).apply {
          isOpaque = true
          add(pane)
        }
      }

      override fun customizeComponent(list: JList<out Any?>?, value: Any?, isSelected: Boolean) {

        super.customizeComponent(list, value, isSelected)
        stateButton?.let { button ->
          stateLabel?.let { lb ->
            button.isVisible = value?.let { vl ->
              if (vl is PopupFactoryImpl.ActionItem) {
                val action = vl.action
                var shortcutText = ""
                if (action.shortcutSet.shortcuts.isNotEmpty()) {
                  shortcutText = KeymapUtil.getShortcutText(action.shortcutSet.shortcuts.first())
                }
                shortcutLabel?.text = shortcutText
                getState(action)?.let {
                  lb.foreground = UIUtil.getLabelForeground()
                  lb.text = it
                  it.isNotEmpty()
                } ?: false
              }
              else false

            } ?: false
          }
        }
      }
    }
  }

  override fun getListElementRenderer(): ListCellRenderer<*> {
    return createRenderer(this)
  }

}
