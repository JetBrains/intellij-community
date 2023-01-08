// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.welcomeScreen

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.openapi.wm.InteractiveCourseFactory
import training.FeaturesTrainerIcons
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.OpenLessonActivities
import training.learn.course.IftModule
import training.ui.views.NewContentLabel
import training.util.iftPluginIsUsing
import training.util.learningPanelWasOpenedInCurrentVersion
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent

internal class IFTInteractiveCourse : InteractiveCourseFactory {
  override fun getInteractiveCourseData(): InteractiveCourseData? =
    if (LangManager.getInstance().getLangSupport()?.useUserProjects == false) IFTInteractiveCourseData() else null
}

private class IFTInteractiveCourseData : InteractiveCourseData {

  override fun getName(): String {
    return LearnBundle.message("welcome.tab.header.learn.ide.features")
  }

  override fun getDescription(): String {
    return LearnBundle.message("welcome.tab.description.learn.ide.features")
  }

  override fun getIcon(): Icon {
    return FeaturesTrainerIcons.PluginIcon
  }

  override fun getActionButtonName(): String {
    return LearnBundle.message("welcome.tab.start.learning.button")
  }

  override fun getAction(): Action {
    return object : AbstractAction(LearnBundle.message("welcome.tab.start.learning.button")) {
      override fun actionPerformed(e: ActionEvent?) {
        openLearningFromWelcomeScreen()
      }
    }
  }
  private fun moduleHasNewContent(module: IftModule): Boolean {
    if (!iftPluginIsUsing) {
      return false
    }
    return module.lessons.any { it.isNewLesson() }
  }

  override fun newContentMarker(): JComponent? {
    if (learningPanelWasOpenedInCurrentVersion) {
      return null
    }
    if (CourseManager.instance.modules.any { moduleHasNewContent(it) }) {
      return NewContentLabel()
    }
    return null
  }

  private fun openLearningFromWelcomeScreen() {
    LangManager.getInstance().getLangSupport()?.startFromWelcomeFrame { selectedSdk: Sdk? ->
      CourseManager.instance.unfoldModuleOnInit = CourseManager.instance.modules.firstOrNull()
      OpenLessonActivities.openLearnProjectFromWelcomeScreen(selectedSdk)
    }
  }

}
