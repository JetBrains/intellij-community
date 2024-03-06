// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.inspections.actions

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.StatusItem
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBColor
import com.intellij.util.FontUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Insets
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

class TrafficLightGroup(val analyzerGetter: () -> AnalyzerStatus, val editor: EditorImpl) : DefaultActionGroup() {
  companion object {
    val INSPECTION_TYPED_ERROR = DataKey.create<StatusItem>("INSPECTION_TYPED_ERROR")
  }

  private val actionList = mutableListOf<InspectionAction>()
  private var base: BaseAction? = null

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (!Registry.`is`("ide.redesigned.inspector", false)) return emptyArray()

    val presentation = e?.presentation ?: return emptyArray()

    val analyzerStatus = analyzerGetter()
    presentation.isVisible = !analyzerStatus.isEmpty()
    if(!presentation.isVisible) return emptyArray()

    val newStatus: List<StatusItem> = analyzerStatus.expandedStatus
    val newIcon: Icon = analyzerStatus.icon




    val bla = if (newStatus.size == 1) newStatus.first() else null

    println("${analyzerStatus.analyzingType} ${analyzerStatus.icon} ${newStatus.isEmpty()} isTextStatus: ${analyzerStatus.isTextStatus()} showNavigation: " +
            "${analyzerStatus.showNavigation} ${bla?.text} ${bla?.detailsText}")

    if (!analyzerStatus.showNavigation) {
      val item = if (newStatus.isEmpty()) StatusItem("", newIcon) else newStatus.first()
      val action = base?.let {
        it.item = item
        it.title = analyzerStatus.title
        it.description = analyzerStatus.details
        it
      } ?: BaseAction(item, editor, analyzerStatus.title, analyzerStatus.details)
      base = action

      return arrayOf(action)
    }

    val arr = mutableListOf<AnAction>()




    newStatus.forEachIndexed { index, item ->
      arr.add(if (index < actionList.size) {
        val action = actionList[index]
        action.item = item
        action
      }
              else {
        val action = InspectionAction(item, editor)
        actionList.add(action)
        action
      })
    }
    return arr.toTypedArray()
  }

  private open class BaseAction(var item: StatusItem, val editor: EditorImpl, var title: @Nls String? = null, var description: @Nls String? = null) : DumbAwareAction(), CustomComponentAction {
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
      component.setForeground(JBColor.lazy { (editor.colorsScheme.getColor(ICON_TEXT_COLOR) ?: ICON_TEXT_COLOR.defaultColor) })
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        init {
          border = JBUI.Borders.empty()
          font = com.intellij.util.ui.JBFont.small()
          setForeground(JBColor.lazy { (editor.colorsScheme.getColor(ICON_TEXT_COLOR) ?: ICON_TEXT_COLOR.defaultColor) })
        }

        override fun iconTextSpace() = JBUI.scale(2)

        override fun checkSkipPressForEvent(e: MouseEvent): Boolean {
          return e.isMetaDown || !(e.button == MouseEvent.BUTTON1 || isSecondActionEvent(e))
        }

        override fun getInsets(): Insets = JBUI.emptyInsets()
        override fun getMargins(): Insets = JBUI.insets(0, 3, 0, 3)

        override fun updateToolTipText() {
          //  val project = editor.project
          if (Registry.`is`("ide.helptooltip.enabled")/* && project != null*/) {
            HelpTooltip.dispose(this)
            HelpTooltip()
              .setTitle(title)
              .setDescription(description)
              /*.setShortcut("  ↩ Return  ")
              .setLink(LangBundle.message("action.ReaderModeProvider.link.configure"))
              { ShowSettingsUtil.getInstance().showSettingsDialog(project, ReaderModeConfigurable::class.java)}*/
              .installOn(this)
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

  private class InspectionAction(item: StatusItem, editor: EditorImpl) : BaseAction(item, editor) {

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
        ActionManager.getInstance().getAction("GotoPreviousError")
      }
                   else {
        ActionManager.getInstance().getAction("GotoNextError")
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
    }

    protected fun getShortcut(): String {
      val shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).shortcuts
      if (shortcuts.size == 0) {
        return "Double" + (if (SystemInfo.isMac) FontUtil.thinSpace() + MacKeymapUtil.SHIFT else " Shift") //NON-NLS
      }
      return KeymapUtil.getShortcutsText(shortcuts)
    }
  }
}