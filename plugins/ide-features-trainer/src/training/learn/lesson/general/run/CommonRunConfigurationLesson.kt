// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.run

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import training.dsl.*
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.ui.LearningUiHighlightingManager
import java.util.concurrent.CompletableFuture
import javax.swing.JButton

abstract class CommonRunConfigurationLesson(id: String) : KLesson(id, LessonsBundle.message("run.configuration.lesson.name")) {
  protected abstract val sample: LessonSample
  protected abstract val demoConfigurationName: String

  private fun TaskRuntimeContext.runManager() = RunManager.getInstance(project)
  protected fun TaskRuntimeContext.configurations() =
    runManager().allSettings.filter { it.name.contains(demoConfigurationName) }

  private fun TaskContext.runToolWindow() = strong(UIBundle.message("tool.window.name.run"))

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

      task {
        val configurationsShown = CompletableFuture<Boolean>()
        triggerByUiComponentAndHighlight<JButton> { ui ->
          if (ui.text == demoConfigurationName) {
            configurationsShown.complete(true)
            true
          }
          else false
        }
        showWarning(LessonsBundle.message("run.configuration.list.not.shown.warning",
                                          strong(ActionsBundle.message("action.ViewNavigationBar.text").dropMnemonic()),
                                          strong(ActionsBundle.message("group.ViewMenu.text").dropMnemonic()),
                                          strong(ActionsBundle.message("group.ViewAppearanceGroup.text").dropMnemonic()))) {
          !configurationsShown.getNow(false)
        }
      }

      val saveConfigurationItemName = ExecutionBundle.message("save.temporary.run.configuration.action.name", demoConfigurationName)
        .dropMnemonic()
      task {
        text(LessonsBundle.message("run.configuration.temporary.to.permanent"))
        triggerByListItemAndHighlight { item ->
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
            jList(saveConfigurationItemName).click()
          }
        }
      }

      task("editRunConfigurations") {
        LearningUiHighlightingManager.clearHighlights()
        text(LessonsBundle.message("run.configuration.edit.configuration",
                                   strong(ActionsBundle.message("action.editRunConfigurations.text").dropMnemonic()),
                                   action(it)))
        triggerByUiComponentAndHighlight<JBCheckBox>(highlightInside = false) { ui ->
          ui.text.contains(ExecutionBundle.message("run.configuration.store.as.project.file").dropMnemonic())
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
    }

  protected abstract fun LessonContext.runTask()

  override val testScriptProperties: TaskTestContext.TestScriptProperties
    get() = TaskTestContext.TestScriptProperties(duration = 20)
}
