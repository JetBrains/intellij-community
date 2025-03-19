// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.inspector

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.HelpTooltip
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.InspectionsFUS
import com.intellij.openapi.editor.markup.StatusItem
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ColorUtil
import com.intellij.ui.GotItTooltip
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import java.awt.Insets
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.lang.Runnable
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

class InspectionsGroup(
  val analyzerGetter: () -> AnalyzerStatus, val editor: EditorImpl
) : DefaultActionGroup(), ActionRemoteBehaviorSpecification.Frontend {
  companion object {
    val INSPECTION_TYPED_ERROR = DataKey.create<StatusItem>("INSPECTION_TYPED_ERROR")
    val idCounter = AtomicInteger(0)
  }

  private val actionList = mutableListOf<InspectionAction>()
  private var base: InspectionsBaseAction? = null
  private val fusTabId = idCounter.incrementAndGet()
  private var myInspectionsSettingAction: InspectionsSettingAction = InspectionsSettingAction(analyzerGetter, fusTabId)

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (!RedesignedInspectionsManager.isAvailable() || PowerSaveMode.isEnabled()) return emptyArray()

    val presentation = e?.presentation ?: return emptyArray()

    val analyzerStatus = analyzerGetter()
    presentation.isVisible = !analyzerStatus.isEmpty()
    if (!presentation.isVisible) return emptyArray()

    val newStatus: List<StatusItem> = analyzerStatus.expandedStatus
    val newIcon: Icon = analyzerStatus.icon

    if (!analyzerStatus.showNavigation) {
      val item = if (newStatus.isEmpty()) StatusItem("", newIcon) else newStatus.first()
      val action = base?.let {
        it.item = item
        it.title = analyzerStatus.title
        it.description = analyzerStatus.details
        it
      } ?: InspectionsBaseAction(item, editor, analyzerStatus.title, analyzerStatus.details, fusTabId = fusTabId)
      base = action

      analyzerStatus.inspectionsState?.let {
        InspectionsFUS.infoStateDetected(e.project, fusTabId, it)
      }

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
        val action = InspectionAction(item, editor, actionLink = actionLink, fusTabId = fusTabId)
        actionList.add(action)
        action
      })


    }
    arr.add(myInspectionsSettingAction)
    return arr.toTypedArray()
  }

  private class InspectionsSettingAction(val analyzerGetter: () -> AnalyzerStatus, val fusTabId: Int) : DumbAwareAction(), CustomComponentAction {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    init {
      templatePresentation.text = DaemonBundle.message("iw.inspection.cog.tooltip")
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

      val comp: JComponent = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return


      SwingUtilities.invokeLater {
        InspectionsFUS.signal(e.project, fusTabId, InspectionsFUS.InspectionsEvent.SHOW_POPUP)
        InspectionsSettingContentService.getInstance().showPopup(analyzerGetter, project, RelativePoint.getSouthWestOf(comp), fusTabId)
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = AllIcons.General.Gear
    }
  }

  private open class InspectionsBaseAction(item: StatusItem, val editor: EditorImpl, var title: @Nls String? = null, var description: @Nls String? = null, var actionLink: Link? = null, protected val fusTabId: Int) : DumbAwareAction(), CustomComponentAction {
    var item = item
      set(value) {
        if(field == value) return
        field = value
        itemUpdated()
      }

    companion object {
      private val ICON_TEXT_COLOR: ColorKey = ColorKey.createColorKey("ActionButton.iconTextForeground",
                                                                      UIUtil.getContextHelpForeground())
    }

    protected open fun itemUpdated(){}

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
          if (UISettings.isIdeHelpTooltipEnabled()) {
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

  private class InspectionAction(item: StatusItem, editor: EditorImpl, actionLink: Link? = null, fusTabId: Int) : InspectionsBaseAction(item, editor, actionLink = actionLink, fusTabId = fusTabId) {
    companion object {
      private val leftRight = DaemonBundle.message("iw.inspection.next.previous", convertSC("Left Click"), convertSC("Right Click"))
      private val url: String = "https://surveys.jetbrains.com/s3/inspection-widget-feedback-form"

      private const val PREVIOUS_ACTION_ID = "GotoPreviousError"
      private const val NEXT_ACTION_ID = "GotoNextError"

      private fun convertSC(str: String): String {
        return "<span style=\"color: ${ColorUtil.toHex(UIUtil.getToolTipForeground())};\"><b>$str</b></span>"
      }

      private fun isGotItAvailable(): Boolean {
        return ApplicationInfoEx.getInstanceEx().isEAP
      }
    }

    init {
      item.detailsText?.let {
        title = DaemonBundle.message("iw.inspection.title", it)
      }
    }

    override fun itemUpdated() {
      super.itemUpdated()
       item.detailsText?.let {
         title = DaemonBundle.message("iw.inspection.title", it)
      } ?: run { title = null }
    }

    override fun isSecondActionEvent(e: InputEvent?): Boolean {
      return e is MouseEvent && e.button == MouseEvent.BUTTON3
    }

    @com.intellij.openapi.components.Service(com.intellij.openapi.components.Service.Level.PROJECT)
    private class MyService(val project: Project, scope: CoroutineScope) {
      companion object {
        fun getInstance(project: Project): MyService = project.service()
      }

      val scope = scope.childScope(supervisor = true, context = Dispatchers.EDT, name = "InspectionWidgetGotItTooltipService")
      private var currentJob: Job? = null

      @Suppress("DEPRECATION")
      fun startGotIt(component: JComponent) {
        ThreadingAssertions.assertEventDispatchThread()
        if (currentJob?.isActive == true || !scope.isActive)
          return

        val job = scope.launch(Dispatchers.EDT) {
          delay(20000)
          val tooltip = GotItTooltip(
            "redesigned.inspections.tooltip",
            DaemonBundle.message("iw.inspection.got.it.text"),
            project
          ).withShowCount(1)
            .withContrastColors(true)
            .withButtonLabel(DaemonBundle.message("iw.inspection.got.it.yes"))
            .withGotItButtonAction {
              BrowserUtil.open(url)
            }
            .withSecondaryButton(DaemonBundle.message("iw.inspection.got.it.no"))

          tooltip.show(component, GotItTooltip.BOTTOM_MIDDLE)
          scope.cancel()
        }

        currentJob = job

        val hierarchyListener = HierarchyListener { e ->
          if (e.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L) {
            if (!component.isDisplayable) {
              job.cancel()
            }
          }
        }
        component.addHierarchyListener(hierarchyListener)

        job.invokeOnCompletion {
          component.removeHierarchyListener(hierarchyListener)
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val actionId = if (isSecondActionEvent(e.inputEvent)) {
        PREVIOUS_ACTION_ID
      }
      else {
        NEXT_ACTION_ID
      }

      val action = ActionManager.getInstance().getAction(actionId) ?: run {
        InspectionsFUS.actionNotFound(e.project, fusTabId, if (actionId == PREVIOUS_ACTION_ID) InspectionsFUS.InspectionsActions.GotoPreviousError else InspectionsFUS.InspectionsActions.GotoNextError)
        return
      }

      val focusManager = IdeFocusManager.getInstance(editor.project)

      val wrapped = AnActionEvent.createFromAnAction(action,
                                                     e.inputEvent,
                                                     ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR,
                                                     wrapDataContext(editor.dataContext))
      InspectionsFUS.performAction(e.project, fusTabId, actionId)

      val project = e.project ?: return

      val performAction = Runnable {
        action.actionPerformed(wrapped)
        GotItTooltip
        e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)?.let {
          if(isGotItAvailable()) {
            MyService.getInstance(project = project).startGotIt(component = it)
          }
        }
      }

      if (focusManager.focusOwner !== editor.contentComponent) {
        focusManager.requestFocus(editor.contentComponent, true).doWhenDone(performAction)
      }
      else {
        performAction.run()
      }
    }

    private fun wrapDataContext(originalContext: DataContext): DataContext =
      CustomizedDataContext.withSnapshot(originalContext) { sink ->
        sink[INSPECTION_TYPED_ERROR] = item
      }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = item.icon
      e.presentation.text = item.text

      val nextKey = getShortcut(NEXT_ACTION_ID)
      val prevKey = getShortcut(PREVIOUS_ACTION_ID)
      val allTypes = DaemonBundle.message("iw.inspection.all.types", convertSC(nextKey), convertSC(prevKey))

      @Suppress("HardCodedStringLiteral")
      description = "<html>$leftRight<p>${allTypes}</html><p>"
    }

    protected fun getShortcut(id: String): String {
      val shortcuts = KeymapUtil.getActiveKeymapShortcuts(id).shortcuts
      return if (shortcuts.isEmpty()) "Not set" else KeymapUtil.getShortcutsText(shortcuts)
    }
  }
}