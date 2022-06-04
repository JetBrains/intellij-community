// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.learn.lesson.general.run

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.ide.ui.UISettings
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.ui.components.JBCheckBox
import training.dsl.*
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiManager
import javax.swing.JButton

abstract class CommonRunConfigurationLesson(id: String) : KLesson(id, LessonsBundle.message("run.configuration.lesson.name")) {
  protected abstract val sample: LessonSample
  protected abstract val demoConfigurationName: String

  private fun TaskRuntimeContext.runManager() = RunManager.getInstance(project)
  protected fun TaskRuntimeContext.configurations() =
    runManager().allSettings.filter { it.name.contains(demoConfigurationName) }

  private fun TaskContext.runToolWindow() = strong(ExecutionBundle.message("tool.window.name.run"))

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)

      prepareRuntimeTask {
        configurations().forEach { runManager().removeConfiguration(it) }
        LessonUtil.setEditorReadOnly(editor)
      }

      runTask()

      task("HideActiveWindow") {
        LearningUiHighlightingManager.clearHighlights()
        text(LessonsBundle.message("run.configuration.hide.toolwindow", runToolWindow(), action(it)))
        checkToolWindowState("Run", false)
        test { actions(it) }
      }

      showWarningIfRunConfigurationsHidden()

      task {
        triggerAndFullHighlight().component { ui: JButton ->
          ui.text == demoConfigurationName
        }
      }

      val saveConfigurationItemName = ExecutionBundle.message("save.temporary.run.configuration.action.name", demoConfigurationName)
        .dropMnemonic()
      task {
        text(LessonsBundle.message("run.configuration.temporary.to.permanent"))
        triggerAndBorderHighlight().listItem { item ->
          item.toString() == saveConfigurationItemName
        }
        test {
          ideFrame {
            button(demoConfigurationName).click()
          }
        }
      }

      task {
        text(LessonsBundle.message("run.configuration.select.save.configuration", strong(saveConfigurationItemName)))
        restoreByUi()
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

      task("editRunConfigurations") {
        LearningUiHighlightingManager.clearHighlights()
        text(LessonsBundle.message("run.configuration.edit.configuration",
                                   strong(ActionsBundle.message("action.editRunConfigurations.text").dropMnemonic()),
                                   action(it)))
        triggerAndBorderHighlight().component { ui: JBCheckBox ->
          ui.text?.contains(ExecutionBundle.message("run.configuration.store.as.project.file").dropMnemonic()) == true
        }
        test {
          actions(it)
        }
      }

      task {
        text(LessonsBundle.message("run.configuration.settings.description"))
        text(LessonsBundle.message("run.configuration.tip.about.save.configuration.into.file"))
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

  protected abstract fun LessonContext.runTask()

  private fun LessonContext.showWarningIfRunConfigurationsHidden() {
    task {
      val step = stateCheck {
        UISettings.getInstance().run { showNavigationBar || showMainToolbar }
      }
      val callbackId = LearningUiManager.addCallback {
        UISettings.getInstance().apply {
          showNavigationBar = true
          fireUISettingsChanged()
        }
        step.complete(true)
      }
      showWarning(LessonsBundle.message("run.configuration.list.not.shown.warning",
                                        strong(ActionsBundle.message("action.ViewNavigationBar.text").dropMnemonic()),
                                        strong(ActionsBundle.message("group.ViewMenu.text").dropMnemonic()),
                                        strong(ActionsBundle.message("group.ViewAppearanceGroup.text").dropMnemonic()),
                                        callbackId)) {
        UISettings.getInstance().run { !showNavigationBar && !showMainToolbar }
      }
    }
  }

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

  override val suitableTips = listOf("SelectRunDebugConfiguration")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("run.configuration.help.link"),
         LessonUtil.getHelpLink("run-debug-configuration.html")),
  )
}
