// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.learn.lesson.general.navigation

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.Switcher
import com.intellij.ide.actions.ui.JBListWithOpenInRightSplit
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBList
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.ui.UIUtil
import training.FeaturesTrainerIcons
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.lesson.LessonManager
import training.util.isToStringContains
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.math.min

abstract class RecentFilesLesson : KLesson("Recent Files and Locations", LessonsBundle.message("recent.files.lesson.name")) {
  abstract override val sampleFilePath: String
  abstract val transitionMethodName: String
  abstract val transitionFileName: String
  abstract val stringForRecentFilesSearch: String  // should look like transitionMethodName
  abstract fun LessonContext.setInitialPosition()

  private val countOfFilesToOpen: Int = 20
  private val countOfFilesToDelete: Int = 5

  override val lessonContent: LessonContext.() -> Unit = {
    sdkConfigurationTasks()

    setInitialPosition()

    task("GotoDeclaration") {
      text(LessonsBundle.message("recent.files.first.transition", code(transitionMethodName), action(it)))
      stateCheck {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@stateCheck false
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return@stateCheck false
        file.name.contains(transitionFileName)
      }
      restoreIfModifiedOrMoved()
      test { actions(it) }
    }

    waitBeforeContinue(500)

    prepareRuntimeTask {
      if (!TaskTestContext.inTestMode) {
        val userDecision = Messages.showOkCancelDialog(
          LessonsBundle.message("recent.files.dialog.message"),
          LessonsBundle.message("recent.files.dialog.title"),
          CommonBundle.message("button.ok"),
          LearnBundle.message("learn.stop.lesson"),
          FeaturesTrainerIcons.PluginIcon
        )
        if (userDecision != Messages.OK) {
          LessonManager.instance.stopLesson()
        }
      }
    }

    openManyFiles()

    task("RecentFiles") {
      text(LessonsBundle.message("recent.files.show.recent.files", action(it)))
      triggerOnRecentFilesShown()
      test { actions(it) }
    }

    task("rfd") {
      text(LessonsBundle.message("recent.files.search.typing", code(it)))
      triggerUI().component { ui: ExtendableTextField ->
        ui.javaClass.name.contains("SpeedSearchBase\$SearchField")
      }
      stateCheck { checkRecentFilesSearch(it) }
      restoreByUi()
      test {
        ideFrame {
          waitComponent(Switcher.SwitcherPanel::class.java)
        }
        type(it)
      }
    }

    task {
      text(LessonsBundle.message("recent.files.search.jump", LessonUtil.rawEnter()))
      stateCheck { virtualFile.name == sampleFilePath.substringAfterLast("/") }
      restoreState(delayMillis = defaultRestoreDelay) {
        !checkRecentFilesSearch("rfd") || previous.ui?.isShowing != true
      }
      test(waitEditorToBeReady = false) {
        invokeActionViaShortcut("ENTER")
      }
    }

    task("RecentFiles") {
      text(LessonsBundle.message("recent.files.use.recent.files.again", action(it)))
      triggerOnRecentFilesShown()
      test { actions(it) }
    }

    var initialRecentFilesCount = -1
    var curRecentFilesCount: Int
    task {
      text(LessonsBundle.message("recent.files.delete", strong(countOfFilesToDelete.toString()),
                                 LessonUtil.rawKeyStroke(KeyEvent.VK_DELETE)))
      triggerUI().component l@{ list: JBListWithOpenInRightSplit<*> ->
        if (list != focusOwner) return@l false
        if (initialRecentFilesCount == -1) {
          initialRecentFilesCount = list.itemsCount
        }
        curRecentFilesCount = list.itemsCount
        initialRecentFilesCount - curRecentFilesCount >= countOfFilesToDelete
      }
      restoreByUi()
      test {
        repeat(countOfFilesToDelete) {
          invokeActionViaShortcut("DELETE")
        }
      }
    }

    task {
      text(LessonsBundle.message("recent.files.close.popup", LessonUtil.rawKeyStroke(KeyEvent.VK_ESCAPE)))
      stateCheck { previous.ui?.isShowing != true }
      test { invokeActionViaShortcut("ESCAPE") }
    }

    task("RecentLocations") {
      text(LessonsBundle.message("recent.files.show.recent.locations", action(it)))
      val recentLocationsText = IdeBundle.message("recent.locations.popup.title")
      triggerUI().component { ui: SimpleColoredComponent ->
        ui.getCharSequence(true) == recentLocationsText
      }
      test { actions(it) }
    }

    task(stringForRecentFilesSearch) {
      text(LessonsBundle.message("recent.files.locations.search.typing", code(it)))
      stateCheck { checkRecentLocationsSearch(it) }
      triggerUI().component { _: SearchTextField -> true } // needed in next task to restore if search field closed
      restoreByUi()
      test {
        ideFrame {
          waitComponent(JBList::class.java)
        }
        type(it)
      }
    }

    task {
      text(LessonsBundle.message("recent.files.locations.search.jump", LessonUtil.rawEnter()))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(transitionFileName)
      }
      stateCheck { virtualFile.name.contains(transitionFileName) }
      restoreState(delayMillis = defaultRestoreDelay) {
        !checkRecentLocationsSearch(stringForRecentFilesSearch) || previous.ui?.isShowing != true
      }
      test {
        waitAndUsePreviouslyFoundListItem { it.doubleClick() }
      }
    }
  }

  // Should open (countOfFilesToOpen - 1) files
  open fun LessonContext.openManyFiles() {
    task {
      addFutureStep {
        val curFile = virtualFile
        val task = object : Task.Backgroundable(project, LessonsBundle.message("recent.files.progress.title"), true) {
          override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false
            val files = curFile.parent?.children?.filter { it.name != curFile.name }
                        ?: throw IllegalStateException("Not found neighbour files for ${curFile.name}")
            for (i in 0 until min(countOfFilesToOpen - 1, files.size)) {
              invokeAndWaitIfNeeded(ModalityState.nonModal()) {
                if (!indicator.isCanceled) {
                  FileEditorManager.getInstance(project).openFile(files[i], true)
                  indicator.fraction = (i + 1).toDouble() / (countOfFilesToOpen - 1)
                }
              }
            }
            taskInvokeLater { completeStep() }
          }
        }

        ProgressManager.getInstance().run(task)
      }
    }
  }

  private fun TaskRuntimeContext.checkRecentFilesSearch(expected: String): Boolean {
    val focusOwner = UIUtil.getParentOfType(Switcher.SwitcherPanel::class.java, focusOwner)
    return focusOwner != null && checkWordInSearch(expected, focusOwner)
  }

  private fun TaskRuntimeContext.checkRecentLocationsSearch(expected: String): Boolean {
    val focusOwner = focusOwner
    return focusOwner is JBList<*> && checkWordInSearch(expected, focusOwner)
  }

  private fun checkWordInSearch(expected: String, component: JComponent): Boolean {
    val supply = SpeedSearchSupply.getSupply(component)
    val enteredPrefix = supply?.enteredPrefix ?: return false
    return enteredPrefix.equals(expected, ignoreCase = true)
  }

  private fun TaskContext.triggerOnRecentFilesShown() {
    val recentFilesText = IdeBundle.message("title.popup.recent.files")
    triggerUI().component { ui: JLabel ->
      ui.text == recentFilesText
    }
  }

  override val testScriptProperties: TaskTestContext.TestScriptProperties
    get() = TaskTestContext.TestScriptProperties(duration = 20)

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("recent.files.locations.help.link"),
         LessonUtil.getHelpLink("navigating-through-the-source-code.html#recent_locations")),
  )
}