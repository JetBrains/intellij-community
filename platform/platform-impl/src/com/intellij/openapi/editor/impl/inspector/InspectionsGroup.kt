// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.inspector

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.StatusItem
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Insets
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

class InspectionsGroup(val analyzerGetter: () -> AnalyzerStatus, val editor: EditorImpl) : DefaultActionGroup() {
  companion object {
    val INSPECTION_TYPED_ERROR = DataKey.create<StatusItem>("INSPECTION_TYPED_ERROR")
  }

  private val actionList = mutableListOf<InspectionAction>()
  private var base: InspectionsBaseAction? = null
  private var myInspectionsSettingAction: InspectionsSettingAction = InspectionsSettingAction(analyzerGetter)

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (!Registry.`is`("ide.redesigned.inspector", false) || PowerSaveMode.isEnabled()) return emptyArray()

    val presentation = e?.presentation ?: return emptyArray()

    val analyzerStatus = analyzerGetter()
    presentation.isVisible = !analyzerStatus.isEmpty()
    if(!presentation.isVisible) return emptyArray()

    val newStatus: List<StatusItem> = analyzerStatus.expandedStatus
    val newIcon: Icon = analyzerStatus.icon

    /*    val bla = if (newStatus.size == 1) newStatus.first() else null

        println("${analyzerStatus.analyzingType} ${analyzerStatus.icon} ${newStatus.isEmpty()} isTextStatus: ${analyzerStatus.isTextStatus()} showNavigation: " +
                "${analyzerStatus.showNavigation} ${bla?.text} ${bla?.detailsText}")*/

  //TODO  PowerSaveMode.isEnabled()

    if (!analyzerStatus.showNavigation) {
      val item = if (newStatus.isEmpty()) StatusItem("", newIcon) else newStatus.first()
      val action = base?.let {
        it.item = item
        it.title = analyzerStatus.title
        it.description = analyzerStatus.details
        it
      } ?: InspectionsBaseAction(item, editor, analyzerStatus.title, analyzerStatus.details)
      base = action

      return arrayOf(action, myInspectionsSettingAction)
    }

    val arr = mutableListOf<AnAction>()
    val actionLink = Link(DaemonBundle.message("iw.inspection.show.all")) { analyzerStatus.controller.toggleProblemsView() }

    newStatus.forEachIndexed { index, item ->
      arr.add(if (index < actionList.size) {
        val action = actionList[index]
        action.item = item
        action
      }
              else {
        val action = InspectionAction(item, editor, actionLink = actionLink)
        actionList.add(action)
        action
      })


    }
    arr.add(myInspectionsSettingAction)
    return arr.toTypedArray()
  }

  private class InspectionsSettingAction(val analyzerGetter: () -> AnalyzerStatus) : DumbAwareAction(), CustomComponentAction {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun createCustomComponent(presentation: Presentation, place: String): ActionButton {
      return object : ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        /*override fun onMousePresenceChanged(setInfo: Boolean) {
          icon = if(setInfo) {
            AllIcons.General.GearPlain
          } else {
            AllIcons.General.Gear
          }
          super.onMousePresenceChanged(setInfo)
        }*/
      }
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
      component.font = com.intellij.util.ui.JBFont.small()
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return

      val comp: JComponent =  e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return
      val panel = InspectionsSettingContent(analyzerGetter, project).panel

      JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, panel).createPopup().show(RelativePoint.getSouthWestOf(comp))
    }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = AllIcons.General.Gear
    }
  }

  private open class InspectionsBaseAction(var item: StatusItem, val editor: EditorImpl, var title: @Nls String? = null, var description: @Nls String? = null, var actionLink: Link? = null) : DumbAwareAction(), CustomComponentAction {
    companion object {
      private val ICON_TEXT_COLOR: ColorKey = ColorKey.createColorKey("ActionButton.iconTextForeground",
                                                                      UIUtil.getContextHelpForeground())
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    protected open fun isSecondActionEvent(e: InputEvent?): Boolean {
      return false
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
      component.font = com.intellij.util.ui.JBFont.small()
      component.setForeground(JBColor.lazy { (editor.colorsScheme.getColor(ICON_TEXT_COLOR) ?: ICON_TEXT_COLOR.defaultColor) })
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {

        init {
          border = JBUI.Borders.empty()
          setForeground(JBColor.lazy { (editor.colorsScheme.getColor(ICON_TEXT_COLOR) ?: ICON_TEXT_COLOR.defaultColor) })
        }

        override fun iconTextSpace() = JBUI.scale(2)

        override fun checkSkipPressForEvent(e: MouseEvent): Boolean {
          return e.isMetaDown || !(e.button == MouseEvent.BUTTON1 || isSecondActionEvent(e))
        }

        override fun getInsets(): Insets = JBUI.emptyInsets()
        override fun getMargins(): Insets = JBUI.insets(0, 3)

        override fun updateToolTipText() {
          if (Registry.`is`("ide.helptooltip.enabled")) {
            HelpTooltip.dispose(this)
            val tooltip = HelpTooltip()
              .setTitle(title)
              .setDescription(description)
            actionLink?.let {
              tooltip.setLink(it.text) { it.action() }
            }
            tooltip.installOn(this)
          }
          else {
            toolTipText = "${title?.let { "$it\n" } ?: ""}${description}"
          }
        }
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = item.icon
      e.presentation.text = item.text
    }
    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  private data class Link(val text: @Nls String, val action: () -> Unit)

  private class InspectionAction(item: StatusItem, editor: EditorImpl, actionLink: Link? = null) : InspectionsBaseAction(item, editor, actionLink = actionLink) {
    companion object{
      private val leftRight = DaemonBundle.message("iw.inspection.next.previous", convertSC("Left Click"), convertSC("Right Click"))

      private const val previousActionId = "GotoPreviousError"
      private const val nextActionId = "GotoNextError"

      private fun convertSC(str: String) : String {
        return "<span style=\"color: ${ColorUtil.toHex(UIUtil.getToolTipForeground())};\"><b>$str</b></span>"
      }
    }

    init {
      item.detailsText?.let {
        title = DaemonBundle.message("iw.inspection.title", it)
      }
    }

    override fun isSecondActionEvent(e: InputEvent?): Boolean {
      return e is MouseEvent && e.button == MouseEvent.BUTTON3
    }

    override fun actionPerformed(e: AnActionEvent) {
      val action = if (isSecondActionEvent(e.inputEvent)) {
        ActionManager.getInstance().getAction(previousActionId)
      }
                   else {
        ActionManager.getInstance().getAction(nextActionId)
      } ?: return


      val focusManager = IdeFocusManager.getInstance(editor.project)

      val delegateEvent = AnActionEvent.createFromAnAction(action,
                                                           e.inputEvent,
                                                           ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR,
                                                           editor.dataContext)

      val wrapped = delegateEvent.withDataContext(wrapDataContext(delegateEvent.dataContext))

      if (focusManager.focusOwner !== editor.contentComponent) {
        focusManager.requestFocus(editor.contentComponent, true).doWhenDone(Runnable { action.actionPerformed(wrapped) })
      }
      else {
        action.actionPerformed(wrapped)
      }
    }

    private fun wrapDataContext(originalContext: DataContext): DataContext =
      CustomizedDataContext.create(originalContext) { dataId ->
        when {
          INSPECTION_TYPED_ERROR.`is`(dataId) -> item
          else -> null
        }
      }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = item.icon
      e.presentation.text = item.text

      val nextKey = getShortcut(nextActionId)
      val prevKey = getShortcut(previousActionId)
      val allTypes = DaemonBundle.message("iw.inspection.all.types", convertSC(nextKey), convertSC(prevKey ))

      description = "<html>$leftRight<p>${allTypes}</html><p>"
    }

    protected fun getShortcut(id: String): String {
      val shortcuts = KeymapUtil.getActiveKeymapShortcuts(id).shortcuts
      return if(shortcuts.isEmpty()) "Not set" else KeymapUtil.getShortcutsText(shortcuts)
    }
  }
}