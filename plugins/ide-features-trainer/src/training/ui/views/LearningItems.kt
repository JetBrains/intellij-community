// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.interfaces.Lesson
import training.learn.interfaces.Module
import training.learn.lesson.LessonManager
import training.ui.UISettings
import training.util.createBalloon
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel

val HOVER_COLOR: Color = JBColor.namedColor("Plugins.hoverBackground", JBColor(0xEDF6FE, 0x464A4D))

class LearningItems : JPanel() {
  var modules: List<Module> = emptyList()
  private val expanded: MutableSet<Module> = mutableSetOf()

  init {
    name = "learningItems"
    layout = VerticalLayout(10)
    border = UISettings.instance.eastBorder
    isOpaque = false
    isFocusable = false
  }

  fun updateItems(showModule: Module? = null) {
    if (showModule != null) expanded.add(showModule)

    layout = VerticalLayout(10)
    removeAll()
    for (module in modules) {
      if (module.lessons.isEmpty()) continue
      add(createModuleItem(module))
      if (expanded.contains(module)) {
        for (lesson in module.lessons) {
          add(createLessonItem(lesson))
        }
      }
    }
    revalidate()
    repaint()
  }

  private fun createLessonItem(lesson: Lesson): JPanel {
    val result = JPanel()
    result.isOpaque = false
    result.layout = HorizontalLayout(5)
    val checkmarkIconLabel = JLabel(if (lesson.passed) FeaturesTrainerIcons.Img.GreenCheckmark else EmptyIcon.ICON_16)
    result.add(JLabel(EmptyIcon.ICON_16))
    result.add(checkmarkIconLabel)

    val name = LinkLabel<Any>(lesson.name, null)
    name.setListener(
      { _, _ ->
        val project = guessCurrentProject(this)
        val cantBeOpenedInDumb = DumbService.getInstance(project).isDumb && !lesson.properties.canStartInDumbMode
        if (cantBeOpenedInDumb && !LessonManager.instance.lessonShouldBeOpenedCompleted(lesson)) {
          val balloon = createBalloon(LearnBundle.message("indexing.message"))
          balloon.showInCenterOf(name)
          return@setListener
        }
        CourseManager.instance.openLesson(project, lesson)
      }, null)
    //name.font = JBFont.label().asPlain().deriveFont(16.0f)
    result.add(name)
    return result
  }

  private fun createModuleItem(module: Module): JPanel {
    val modulePanel = JPanel()
    modulePanel.isOpaque = false
    modulePanel.layout = VerticalLayout(5)
    modulePanel.background = Color(0, 0, 0, 0)

    val result = JPanel()
    result.isOpaque = true
    result.background = UISettings.instance.backgroundColor

    result.toolTipText = module.description

    result.layout = HorizontalLayout(5)

    result.border = JBUI.Borders.empty(5, 7)

    val expandPanel = JPanel().also {
      it.layout = VerticalLayout(5)
      it.isOpaque = false
      it.background = Color(0, 0, 0, 0)
      val expandIcon = IconUtil.toSize(if (expanded.contains(module)) UIUtil.getTreeExpandedIcon() else UIUtil.getTreeCollapsedIcon(),
                                       JBUIScale.scale(16), JBUIScale.scale(16))
      val expandIconLabel = JLabel(expandIcon)
      it.add(expandIconLabel)
    }
    result.add(expandPanel)

    val name = JLabel(module.name)
    name.font = UISettings.instance.modulesFont
    modulePanel.add(name)

    createModuleProgressLabel(module)?.let {
      modulePanel.add(it)
    }
    result.add(modulePanel)

    result.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (!result.visibleRect.contains(e.point)) return

        if (expanded.contains(module)) {
          expanded.remove(module)
        }
        else {
          expanded.clear()
          expanded.add(module)
        }
        updateItems()
      }

      override fun mouseEntered(e: MouseEvent) {
        result.background = HOVER_COLOR
        result.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        result.revalidate()
        result.repaint()
      }

      override fun mouseExited(e: MouseEvent) {
        result.background = UISettings.instance.backgroundColor
        result.cursor = Cursor.getDefaultCursor()
        result.revalidate()
        result.repaint()
      }
    })
    return result
  }

  private fun createModuleProgressLabel(module: Module): JBLabel? {
    val progressStr = module.calcProgress() ?: return null
    val progressLabel = JBLabel(progressStr)
    progressLabel.name = "progressLabel"
    progressLabel.foreground = if (module.hasNotPassedLesson()) UISettings.instance.moduleProgressColor else UISettings.instance.completedColor
    return progressLabel
  }
}
