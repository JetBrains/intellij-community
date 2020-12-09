// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.general.run

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.jList
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import training.commands.kotlin.TaskContext
import training.commands.kotlin.TaskRuntimeContext
import training.commands.kotlin.TaskTestContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.*
import training.ui.LearningUiHighlightingManager
import javax.swing.JButton

abstract class CommonRunConfigurationLesson(module: Module, id: String, languageId: String)
  : KLesson(id, LessonsBundle.message("run.configuration.lesson.name"), module, languageId) {
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

      actionTask("HideActiveWindow") {
        LearningUiHighlightingManager.clearHighlights()
        LessonsBundle.message("run.configuration.hide.toolwindow", runToolWindow(), action(it))
      }

      task {
        triggerByUiComponentAndHighlight<JButton> { ui ->
          ui.text == demoConfigurationName
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
          ui.text == ExecutionBundle.message("run.configuration.store.as.project.file").dropMnemonic()
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
        test {
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
