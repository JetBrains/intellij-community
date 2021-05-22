// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.project

import com.intellij.idea.ActionsBundle
import training.dsl.LessonUtil
import training.dsl.dropMnemonic
import training.learn.LearnBundle
import training.learn.LessonsBundle

open class ReadMeCreator {
  private val learnName get() = LearnBundle.message("toolwindow.stripe.Learn")
  private val fileMenu get() = ActionsBundle.message("group.FileMenu.text").dropMnemonic()
  private val helpMenu get() = ActionsBundle.message("group.HelpMenu.text").dropMnemonic()
  private val closeProject = ActionsBundle.message("action.CloseProject.text").dropMnemonic()
  private val iftAccess = LearnBundle.message("action.ShowLearnPanel.text").dropMnemonic()

  private  val bugTracker = "https://youtrack.jetbrains.com/issues/IFT"

  protected open val welcomeHeader: String = LessonsBundle.message("readme.welcome.header")
  protected open val iftDescription: String = LessonsBundle.message("readme.ift.description", LessonUtil.productName)

  protected open val howToUseHeader: String = LessonsBundle.message("readme.usage.header")
  protected open val toolWindowDescription: String = LessonsBundle.message("readme.toolwindow.description", learnName)
  protected open val experiencedUsersRemark: String = LessonsBundle.message("readme.experienced.users.remark")

  protected open val startingHeader: String = LessonsBundle.message("readme.start.header")
  protected open val indexingDescription: String =
    LessonsBundle.message("readme.indexing.description", 1) + " " +
    LessonsBundle.message("readme.navigation.hint", LessonsBundle.message("navigation.module.name"))

  protected open val shortcutsHeader: String = LessonsBundle.message("readme.shortcuts.header")
  protected open val shortcutProblemDescription: String = LessonsBundle.message("readme.shortcuts.problem.description")
  protected open val bugTrackerRemark: String = LessonsBundle.message("readme.bug.tracker.remark", bugTracker)

  protected open val exitHeader: String = LessonsBundle.message("readme.conclusion.header")
  protected open val exitOptionsDescription: String = LessonsBundle.message("readme.exit.options",
                                                                            fileMenu, closeProject, learnName, helpMenu, iftAccess)
  protected open val feedbackRequest: String = LessonsBundle.message("readme.feedback.request")

  open fun createReadmeMdText(): String = """
### $welcomeHeader

$iftDescription

##### $howToUseHeader

$toolWindowDescription

$experiencedUsersRemark

##### $startingHeader

$indexingDescription

##### $shortcutsHeader

$shortcutProblemDescription

$bugTrackerRemark

##### $exitHeader

$exitOptionsDescription

$feedbackRequest
  """ // do not use trim because derived implementations will need to follow the current indent
}