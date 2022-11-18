// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.welcomeScreen

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.InteractiveCoursePanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import training.FeaturesTrainerIcons
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.OpenLessonActivities
import training.learn.course.IftModule
import training.ui.views.NewContentLabel
import training.util.iftPluginIsUsing
import training.util.learningPanelWasOpenedInCurrentVersion
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLDocument

internal class IFTInteractiveCourse : InteractiveCourseFactory {

  override val isActive: Boolean get() = LangManager.getInstance().getLangSupport()?.useUserProjects == false

  override fun getInteractiveCourseComponent(): JComponent = IFTInteractiveCoursePanel()
}

private class IFTInteractiveCoursePanel : InteractiveCoursePanel(IFTInteractiveCourseData(), !ExperimentalUI.isNewUI()) {
  init {
    if (ExperimentalUI.isNewUI()) {
      add(JTextPane().apply {
        contentType = "text/html"
        addHyperlinkListener(object : HyperlinkAdapter() {
          override fun hyperlinkActivated(e: HyperlinkEvent) {
            Registry.get("ide.experimental.ui").setValue(false)
          }
        })
        editorKit = HTMLEditorKitBuilder.simple()
        text = LearnBundle.message("welcome.tab.toggle.new.ui.hint")

        val styleSheet = (document as HTMLDocument).styleSheet
        val textColor = "#" + ColorUtil.toHex(UIUtil.getLabelInfoForeground())
        styleSheet.addRule("body { color: $textColor; font-size:${JBUI.Fonts.label().lessOn(3f)}pt;}")
        isEditable = false
        @Suppress("UseJBColor")
        background = Color(0, true)
        border = JBUI.Borders.empty(14, leftMargin, 0, 0)
        alignmentX = Component.LEFT_ALIGNMENT
      })
    }
  }
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
