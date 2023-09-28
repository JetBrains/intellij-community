// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.chooser.actions.*
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import java.util.function.Supplier
import javax.swing.*

class ImportSettingsFromDialog : DialogWrapper(null) {
  private val accountLabel = JLabel("user.name").apply {
    icon = AllIcons.General.User
  }

  private val pane = JPanel(VerticalLayout(JBUI.scale(36), SwingConstants.CENTER)).apply {
    add(JLabel("Import Settings").apply {
      font = Font(font.getFontName(), Font.PLAIN, JBUIScale.scaleFontSize(24f))
    })
  }

  init {
    val group = DefaultActionGroup()
    group.isPopup = false
    val callback: (Int)-> Unit = {
      close(OK_EXIT_CODE)
    }

    group.add(SyncStateAction())
    group.add(SyncChooserAction(callback))
    group.add(JbChooserAction(callback))
    group.add(ExpChooserAction(callback))
    group.add(SkipImportAction())

    val actionButtonLook = object : IdeaActionButtonLook() {
      override fun paintBorder(g: Graphics, component: JComponent, state: Int) {
        if (component is ActionButtonWithText && component.action is LinkAction) {
          return
        }

        val rect = Rectangle(component.size)
        JBInsets.removeFrom(rect, component.getInsets())
        /*
        val color = when(state) {
          ActionButtonComponent.PUSHED -> JBColor.namedColor("Button.startBorderColor", JBColor(0xa8adbd, 0x6f737a))
          ActionButtonComponent.POPPED -> JBColor.namedColor("Button.default.borderColor", JBColor(0xa8adbd, 0x6f737a))
          else -> JBColor.namedColor("Button.default.borderColor", JBColor(0xa8adbd, 0x6f737a))
        }
        */
        val color = when (state) {
          ActionButtonComponent.PUSHED -> Color.RED
          ActionButtonComponent.POPPED -> Color.BLUE
          else -> Color.GRAY
        }
        paintLookBorder(g, rect, color)
      }

      override fun paintBackground(g: Graphics?, component: JComponent?, state: Int) {
        if (component is ActionButtonWithText && component.action is SkipImportAction) {
          super.paintBackground(g, component, state)
          return
        }
      }
    }

    val act = createActionToolbar(group, false).apply {
      if (this is ActionToolbarImpl) {

        setMinimumButtonSize {
          JBUI.size(UiUtils.DEFAULT_BUTTON_WIDTH, UiUtils.DEFAULT_BUTTON_HEIGHT)
        }
        setMiniMode(false)
        setActionButtonBorder(4, JBUI.CurrentTheme.RunWidget.toolbarBorderHeight())

        setCustomButtonLook(actionButtonLook)
      }
    }
    act.targetComponent = pane

  //  val skipImport = OnboardingDialogButtons.createHoveredLinkButton("Skip Import", null, {})

    pane.add(act.component)
    /*    pane.add(JPanel(VerticalLayout(JBUI.scale(12), SwingConstants.CENTER)).apply {
      add(act.component)
    })*/
    init()
  }

  private fun createActionToolbar(group: ActionGroup, horizontal: Boolean): ActionToolbar {
    return object : ActionToolbarImpl(ActionPlaces.IMPORT_SETTINGS_DIALOG, group, horizontal){
      override fun createToolbarButton(action: AnAction,
                                       look: ActionButtonLook?,
                                       place: String,
                                       presentation: Presentation,
                                       minimumSize: Supplier<out Dimension>?): ActionButton {
        val actionButton = super.createToolbarButton(action, look, place, presentation, minimumSize)
        if(actionButton.action is LinkAction) {
          actionButton.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
        }

        return actionButton
      }

      init {
        isOpaque = false
        setNoGapMode()
      }

      override fun getPreferredSize(): Dimension {
        val dm = super.getPreferredSize()
        if(horizontal) {
          dm.width -= 10
        } else dm.height -=10
          return dm
      }
    }



  }



  /*  override fun createDefaultActions() {
      super.createDefaultActions()
      init()
    }*/

  private fun showListPopup() {

  }

  override fun createCenterPanel(): JComponent {
    return JPanel(GridBagLayout()).apply {
      preferredSize = JBDimension(640, 410)
      val gbc = GridBagConstraints()
      gbc.gridx = 0
      gbc.gridy = 0
      gbc.weightx = 1.0
      gbc.weighty = 1.0
      gbc.fill = GridBagConstraints.NONE
      add(pane, gbc)
    }
  }

  override fun createActions(): Array<Action> {
    return emptyArray()
  }

  /*
  override fun createSouthAdditionalPanel(): JPanel? {
    return JPanel(BorderLayout()).apply {
      add(accountLabel, BorderLayout.CENTER)
    }
  }
  */


  override fun createSouthPanel(leftSideButtons: MutableList<out JButton>,
                                rightSideButtons: MutableList<out JButton>,
                                addHelpToLeftSide: Boolean): JPanel {
    val group = DefaultActionGroup()
    group.add(OtherOptions({}))

    val at = createActionToolbar(group, true)
    at.targetComponent = pane

    return JPanel(BorderLayout()).apply {
      add(accountLabel, BorderLayout.WEST)
      add(at.component, BorderLayout.EAST)

    }


/*    val panel = super.createSouthPanel(leftSideButtons, rightSideButtons, addHelpToLeftSide)

    val group = DefaultActionGroup()
    group.add(OtherOptions())

    val at = createActionToolbar(group, true)
    at.targetComponent = pane

    panel.add(at.component, BorderLayout.EAST)*/

/*    panel.add(JPanel(GridBagLayout()).apply {
      val c = GridBagConstraints()
      c.fill = GridBagConstraints.NONE
      c.anchor = GridBagConstraints.BASELINE

      val group = DefaultActionGroup()
      group.add(OtherOptions())

      val at = createActionToolbar(group, true)
      at.targetComponent = pane
      add(at.component, c)

    }, BorderLayout.EAST)*/

    //return panel
  }
}
