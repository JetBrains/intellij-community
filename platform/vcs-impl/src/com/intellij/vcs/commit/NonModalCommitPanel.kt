// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.actions.ShowCommitOptionsAction
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativePoint.getNorthEastOf
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.EventDispatcher
import com.intellij.util.IJSwingUtilities.updateComponentTreeUI
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.util.ui.UIUtil.uiTraverser
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.LayoutManager
import java.awt.Point
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

private fun panel(layout: LayoutManager): JBPanel<*> = JBPanel<JBPanel<*>>(layout)

abstract class NonModalCommitPanel(
  val project: Project,
  val commitActionsPanel: CommitActionsPanel = CommitActionsPanel(),
  val commitAuthorComponent: CommitAuthorComponent = CommitAuthorComponent(project)
) : BorderLayoutPanel(),
    NonModalCommitWorkflowUi,
    CommitActionsUi by commitActionsPanel,
    CommitAuthorTracker by commitAuthorComponent,
    ComponentContainer,
    EditorColorsListener {

  private val inclusionEventDispatcher = EventDispatcher.create(InclusionListener::class.java)
  private val dataProviders = mutableListOf<DataProvider>()
  private var needUpdateCommitOptionsUi = false

  protected val centerPanel = simplePanel()
  protected var bottomPanel: JBPanel<*>.() -> Unit = { }

  private val actions = ActionManager.getInstance().getAction("ChangesView.CommitToolbar") as ActionGroup
  val toolbar = ActionManager.getInstance().createActionToolbar(COMMIT_TOOLBAR_PLACE, actions, true).apply {
    setTargetComponent(this@NonModalCommitPanel)
    component.isOpaque = false
  }

  val commitMessage = CommitMessage(project, false, false, true, message("commit.message.placeholder")).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(6)) }
  }

  override val commitMessageUi: CommitMessageUi get() = commitMessage

  override fun getComponent(): JComponent = this
  override fun getPreferredFocusableComponent(): JComponent = commitMessage.editorField

  override fun getData(dataId: String) = getDataFromProviders(dataId) ?: commitMessage.getData(dataId)
  fun getDataFromProviders(dataId: String) = dataProviders.asSequence().mapNotNull { it.getData(dataId) }.firstOrNull()

  override fun addDataProvider(provider: DataProvider) {
    dataProviders += provider
  }

  override fun addInclusionListener(listener: InclusionListener, parent: Disposable) =
    inclusionEventDispatcher.addListener(listener, parent)

  protected fun fireInclusionChanged() = inclusionEventDispatcher.multicaster.inclusionChanged()

  override fun startBeforeCommitChecks() = Unit
  override fun endBeforeCommitChecks(result: CheckinHandler.ReturnResult) = Unit

  override fun dispose() = Unit

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    needUpdateCommitOptionsUi = true
    commitActionsPanel.border = getButtonPanelBorder()
  }

  protected fun buildLayout() {
    commitActionsPanel.apply {
      border = getButtonPanelBorder()
      background = getButtonPanelBackground()

      setTargetComponent(this@NonModalCommitPanel)
    }
    centerPanel
      .addToCenter(commitMessage)
      .addToBottom(panel(VerticalLayout(0)).apply {
        background = getButtonPanelBackground()
        bottomPanel()
      })

    addToCenter(centerPanel)
    withPreferredHeight(85)
  }

  private fun getButtonPanelBorder(): Border {
    return EmptyBorder(0, scale(3), (scale(6) - commitActionsPanel.getBottomInset()).coerceAtLeast(0), 0)
  }

  private fun getButtonPanelBackground(): JBColor? {
    return JBColor.lazy { (commitMessage.editorField.editor as? EditorEx)?.backgroundColor ?: getTreeBackground() }
  }

  override fun showCommitOptions(options: CommitOptions, actionName: String, isFromToolbar: Boolean, dataContext: DataContext) {
    val commitOptionsPanel = CommitOptionsPanel { actionName }.apply {
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
      isFocusCycleRoot = true

      setOptions(options)
      border = empty(0, 10)

      // to reflect LaF changes as commit options components are created once per commit
      if (needUpdateCommitOptionsUi) {
        needUpdateCommitOptionsUi = false
        updateComponentTreeUI(this)
      }
    }
    val focusComponent = IdeFocusManager.getInstance(project).getFocusTargetFor(commitOptionsPanel)
    val commitOptionsPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(commitOptionsPanel, focusComponent)
      .setRequestFocus(true)
      .createPopup()

    showCommitOptions(commitOptionsPopup, isFromToolbar, dataContext)
  }

  protected open fun showCommitOptions(popup: JBPopup, isFromToolbar: Boolean, dataContext: DataContext) =
    if (isFromToolbar) popup.show(getNorthEastOf(toolbar.getShowCommitOptionsButton() ?: toolbar.component))
    else popup.showInBestPositionFor(dataContext)

  companion object {
    internal const val COMMIT_TOOLBAR_PLACE: String = "ChangesView.CommitToolbar"

    fun JBPopup.showAbove(component: JComponent) {
      val northWest = RelativePoint(component, Point())

      addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          val popup = event.asPopup()
          val location = Point(popup.locationOnScreen).apply { y = northWest.screenPoint.y - popup.size.height }

          popup.setLocation(location)
        }
      })
      show(northWest)
    }
  }
}

private fun ActionToolbar.getShowCommitOptionsButton(): JComponent? =
  uiTraverser(component)
    .filter(ActionButton::class.java)
    .find { it.action is ShowCommitOptionsAction }
