// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.learn.lesson.general.run

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.execution.ui.RunConfigurationStartHistory
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.JBUI
import training.dsl.*
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.ui.LearningUiHighlightingManager
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JList

abstract class CommonRunConfigurationLesson(id: String) : KLesson(id, LessonsBundle.message("run.configuration.lesson.name")) {
  protected abstract val sample: LessonSample
  protected abstract val demoConfigurationName: String

  private fun TaskRuntimeContext.runManager() = RunManager.getInstance(project)
  protected fun TaskRuntimeContext.configurations() =
    runManager().allSettings.filter { it.name.contains(demoConfigurationName) }

  protected val demoWithParametersName: String get() = "$demoConfigurationName with parameters"

  private val runIcon: Icon by lazy { toStrokeIcon(AllIcons.Actions.Execute, JBUI.CurrentTheme.RunWidget.RUN_ICON_COLOR) }

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      prepareRuntimeTask {
        val configurations = configurations()
        for (it in configurations) {
          runManager().removeConfiguration(it)
        }
        RunConfigurationStartHistory.getInstance(project).loadState(RunConfigurationStartHistory.State())
        RunManager.getInstance(project).selectedConfiguration = null
        LessonUtil.setEditorReadOnly(editor)
      }

      highlightButtonById("Run", highlightInside = false, usePulsation = false)

      task {
        text(LessonsBundle.message("run.configuration.run.current", icon(runIcon)))
        text(LessonsBundle.message("run.configuration.run.current.balloon"), LearningBalloonConfig(Balloon.Position.below, 0))
        checkToolWindowState("Run", true)
        test {
          ideFrame {
            highlightedArea.click()
          }
        }
      }

      text(LessonsBundle.message("run.configuration.no.run.configuration",
                                 strong(ExecutionBundle.message("run.configurations.combo.run.current.file.selected"))))

      runTask()

      lateinit var restoreMoreTask: TaskContext.TaskId
      highlightButtonById("MoreRunToolbarActions", highlightInside = false, usePulsation = false) {
        restoreMoreTask = taskId
      }

      val saveConfigurationItemName = ExecutionBundle.message("choose.run.popup.save")
      task {
        text(LessonsBundle.message("run.configuration.temporary.to.permanent", actionIcon("MoreRunToolbarActions")))
        text(LessonsBundle.message("run.configuration.open.additional.menu.balloon"),
             LearningBalloonConfig(Balloon.Position.below, 0, highlightingComponent = LessonUtil.lastHighlightedUi()))
        triggerAndBorderHighlight().listItem { item ->
          item is PopupFactoryImpl.ActionItem && item.text == saveConfigurationItemName
        }
        test {
          ideFrame {
            highlightedArea.click()
          }
        }
      }

      task {
        text(LessonsBundle.message("run.configuration.select.save.configuration", strong(saveConfigurationItemName)))
        restoreByUi(restoreId = restoreMoreTask, delayMillis = defaultRestoreDelay)
        stateCheck {
          val selectedConfiguration = RunManager.getInstance(project).selectedConfiguration ?: return@stateCheck false
          !selectedConfiguration.isTemporary
        }
        test {
          ideFrame {
            jList(saveConfigurationItemName).item(saveConfigurationItemName).click()
          }
        }
      }

      addAnotherRunConfiguration()

      lateinit var dropDownTask: TaskContext.TaskId
      highlightButtonById("RedesignedRunConfigurationSelector", usePulsation = false) {
        dropDownTask = taskId
      }

      var foundItem = 0

      task {
        before {
          // Just for sure
          RunConfigurationStartHistory.getInstance(project).let {
            it.state.allConfigurationsExpanded = true
            it.state.history = mutableSetOf()
            it.state.pinned = mutableSetOf()
          }
        }
        text(LessonsBundle.message("run.configuration.open.run.configurations.popup"))
        triggerAndBorderHighlight().componentPart { jList: JList<*> ->
          foundItem = LessonUtil.findItem(jList) { item ->
            item is PopupFactoryImpl.ActionItem && item.text == demoWithParametersName
          } ?: return@componentPart null

          jList.getCellBounds(foundItem, foundItem)
        }
        test {
          ideFrame {
            highlightedArea.click()
          }
        }
      }

      task {
        text(LessonsBundle.message("run.configuration.hover.generated.configuration"))
        addFutureStep {
          val jList = previous.ui as? JList<*> ?: return@addFutureStep
          jList.addListSelectionListener { _ ->
            if (jList.selectedIndex == foundItem) {
              completeStep()
            }
          }
        }
        restoreByUi(restoreId = dropDownTask)
        test {
          ideFrame {
            highlightedArea.hover()
          }
        }
      }

      task {
        before {
          val ui = previous.ui as? JList<*> ?: return@before
          LearningUiHighlightingManager.highlightPartOfComponent(ui, LearningUiHighlightingManager.HighlightingOptions(highlightInside = false)) {
            val itemRect = ui.getCellBounds(foundItem, foundItem)
            Rectangle(itemRect.x + itemRect.width - JBUI.scale(110), itemRect.y, JBUI.scale(35), itemRect.height)
          }
        }
        text(LessonsBundle.message("run.configuration.run.generated.configuration"))
        stateCheck {
          val settings = RunManager.getInstance(project).allSettings.associateBy { it.uniqueID }
          RunConfigurationStartHistory.getInstance(project).history().asSequence().mapNotNull { settings[it] }
            .firstOrNull()?.configuration?.name == demoWithParametersName
        }
        restoreByUi(restoreId = dropDownTask)
        test {
          ideFrame {
            highlightedArea.click()
          }
        }
      }

      highlightButtonById("RedesignedRunConfigurationSelector", usePulsation = false)

      task("editRunConfigurations") {
        text(LessonsBundle.message("run.configuration.edit.configuration",
                                   LessonUtil.rawShift(),
                                   strong(ActionsBundle.message("action.editRunConfigurations.text").dropMnemonic())))
        triggerAndBorderHighlight().component { ui: JBCheckBox ->
          ui.text?.contains(ExecutionBundle.message("run.configuration.store.as.project.file").dropMnemonic()) == true
        }
        test {
          actions(it)
        }
      }

      task {
        text(LessonsBundle.message("run.configuration.settings.description"))
        gotItStep(Balloon.Position.below, 300, LessonsBundle.message("run.configuration.tip.about.save.configuration.into.file"))
      }

      task {
        before {
          LearningUiHighlightingManager.clearHighlights()
        }
        text(LessonsBundle.message("run.configuration.close.settings"))
        stateCheck {
          focusOwner is EditorComponentImpl
        }
        test(waitEditorToBeReady = false) {
          ideFrame {
            button("Cancel").click()
          }
        }
      }

      restoreUiInformer()
    }

  protected open fun LessonContext.addAnotherRunConfiguration() {}

  protected abstract fun LessonContext.runTask()

  private fun LessonContext.restoreUiInformer() {
    if (UISettings.getInstance().run { showNavigationBar || showMainToolbar }) return
    restoreChangedSettingsInformer {
      UISettings.getInstance().apply {
        showNavigationBar = false
        showMainToolbar = false
        fireUISettingsChanged()
      }
    }
  }

  override val testScriptProperties: TaskTestContext.TestScriptProperties
    get() = TaskTestContext.TestScriptProperties(duration = 20)

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("run.configuration.help.link"),
         LessonUtil.getHelpLink("run-debug-configuration.html")),
  )
}
