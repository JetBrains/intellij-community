// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.execution.target.TargetEnvironmentWizardStepKt
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import training.learn.LearnBundle
import training.statistic.StatisticBase
import training.util.KeymapUtil
import training.util.invokeActionForFocusContext
import java.awt.Component
import java.awt.Insets
import java.awt.Point
import javax.swing.JLabel
import javax.swing.JPanel

internal fun showActionKeyPopup(parent: Component, point: Point, height: Int, actionId: String) {
  val action = ActionManager.getInstance().getAction(actionId)
  if (action == null) return

  lateinit var balloon: Balloon
  val jPanel = JPanel()
  jPanel.layout = VerticalLayout(TargetEnvironmentWizardStepKt.VGAP, 250)
  jPanel.isOpaque = false
  jPanel.add(JLabel(action.templatePresentation.text))
  val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)
  for (shortcut in shortcuts) {
    if (shortcut is KeyboardShortcut) {
      val keyStrokeText = KeymapUtil.getKeyStrokeText(shortcut.firstKeyStroke)
      val shortcutLabel = JLabel(if (SystemInfo.isMac) KeymapUtil.decryptMacShortcut(keyStrokeText) else keyStrokeText).also {
        it.font = it.font.deriveFont((it.font.size - 1).toFloat())
        it.foreground = JBUI.CurrentTheme.Tooltip.shortcutForeground()
      }
      jPanel.add(shortcutLabel)
    }
  }

  jPanel.add(ActionLink(LearnBundle.message("shortcut.balloon.apply.this.action")) {
    invokeActionForFocusContext(action)
    balloon.hide()
  })
  jPanel.add(ActionLink(LearnBundle.message("shortcut.balloon.add.shortcut")) {
    KeymapPanel.addKeyboardShortcut(actionId, ActionShortcutRestrictions.getInstance().getForActionId(actionId),
                                    KeymapManager.getInstance().activeKeymap, parent)
    balloon.hide()
    parent.repaint()
  })
  val builder = JBPopupFactory.getInstance()
    .createBalloonBuilder(jPanel)
    .setShowCallout(true)
    .setHideOnKeyOutside(true)
    .setHideOnClickOutside(true)
    .setAnimationCycle(0)
    .setCalloutShift(height/2 + 1)
    .setCornerToPointerDistance(80)
    .setBlockClicksThroughBalloon(true)
    .setBorderColor(JBUI.CurrentTheme.Tooltip.borderColor())
    .setFillColor(UIUtil.getToolTipBackground())
    .setBorderInsets(Insets(8, 10, 8, 10))
    .setShadow(true)
  balloon = builder.createBalloon()
  balloon.show(RelativePoint(parent, point), Balloon.Position.below)

  StatisticBase.logShortcutClicked(actionId)
}
