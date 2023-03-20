// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.zoomIndicator

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel

class ZoomIndicatorView(val editor: EditorImpl) : JPanel(MigLayout("novisualpadding, ins 0")) {
  var isHovered = false; private set
  var lastHoverMs = 0L; private set

  private val fontSizeLabel = JLabel(IdeBundle.message("action.reset.font.size.info", "000"))

  private val settingsAction = object : DumbAwareAction(IdeBundle.message("action.open.editor.settings.text"), "", AllIcons.General.Settings) {
    override fun actionPerformed(e: AnActionEvent) {
      val prj = e.project ?: return
      val searchName = ApplicationBundle.message("checkbox.enable.ctrl.mousewheel.changes.font.size.hint")
      ShowSettingsUtilImpl.showSettingsDialog(prj, "preferences.editor", searchName)
    }
  }

  private val settingsBtn = object : ActionButton(settingsAction, settingsAction.templatePresentation.clone(), ActionPlaces.POPUP, JBUI.size(22, 22)) {
    override fun performAction(e: MouseEvent?) {
      val event = AnActionEvent.createFromInputEvent(e, myPlace, myPresentation, dataContext, false, true)
      ActionUtil.performDumbAwareWithCallbacks(myAction, event) { actionPerformed(event) }
    }
    override fun isShowing() = true
  }

  private val dataContext = DataContext {
    if (CommonDataKeys.EDITOR.`is`(it)) editor
    else null
  }

  private inner class PatchedActionLink(action: AnAction, event: AnActionEvent) : AnActionLink(action, ActionPlaces.POPUP) {
    init {
      text = event.presentation.text
      autoHideOnDisable = false
      isEnabled = event.presentation.isEnabled
      event.presentation.addPropertyChangeListener {
        if (it.propertyName == Presentation.PROP_ENABLED) {
          isEnabled = it.newValue as Boolean
        }
      }
    }
    override fun getData(dataId: String) = dataContext.getData(dataId) ?: super.getData(dataId)
    override fun isShowing() = true
  }

  private val resetLink = ActionManager.getInstance().getAction("EditorResetFontSize").run {
    val event = AnActionEvent.createFromInputEvent(null, ActionPlaces.POPUP,
                                                   null, dataContext,
                                                   false, true)
    update(event)
    fontSizeLabel.addPropertyChangeListener {
      if (it.propertyName == "text") {
        update(event)
      }
    }

    PatchedActionLink(this, event)
  }

  init {
    val mouseListener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) {
        isHovered = true
        lastHoverMs = System.currentTimeMillis()
      }
      override fun mouseExited(e: MouseEvent?) { isHovered = false }
    }
    addMouseListener(mouseListener)
    resetLink.addMouseListener(mouseListener)
    settingsBtn.addMouseListener(mouseListener)

    val disposable = Disposer.newDisposable()
    EditorUtil.disposeWithEditor(editor, disposable)

    updateFontSize()

    add(fontSizeLabel, "wmin 100, gapbottom 1, gapleft 3")
    add(resetLink, "gapbottom 1")
    add(settingsBtn)
  }

  fun updateFontSize() {
    fontSizeLabel.text = IdeBundle.message("action.reset.font.size.info", editor.fontSize)
  }
}