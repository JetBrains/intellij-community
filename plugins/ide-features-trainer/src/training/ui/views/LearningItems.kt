// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui.views

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import training.FeaturesTrainerIcons
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.course.IftModule
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.statistic.LessonStartingWay
import training.ui.UISettings
import training.util.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

private val HOVER_COLOR: Color get() = JBColor.namedColor("Plugins.hoverBackground", JBColor(0xEDF6FE, 0x464A4D))

class LearningItems(private val project: Project) : JPanel() {
  var modules: Collection<IftModule> = emptyList()
  private val expanded: MutableSet<IftModule> = mutableSetOf()

  init {
    name = "learningItems"
    layout = VerticalLayout(0, UISettings.getInstance().let { it.panelWidth - (it.westInset + it.eastInset) })
    isOpaque = false
    isFocusable = false
  }

  fun updateItems(showModule: IftModule? = null) {
    if (showModule != null) expanded.add(showModule)

    removeAll()
    for (module in modules) {
      if (module.lessons.isEmpty()) continue
      add(createModuleItem(module), VerticalLayout.FILL_HORIZONTAL)
      if (expanded.contains(module)) {
        for (lesson in module.lessons) {
          add(createLessonItem(lesson), VerticalLayout.FILL_HORIZONTAL)
        }
      }
    }
    revalidate()
    repaint()
  }

  private fun createLessonItem(lesson: Lesson): JPanel {
    val name = JLabel(lesson.name).also {
      it.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
    }
    val clickAction: () -> Unit = l@{
      val cantBeOpenedInDumb = DumbService.getInstance(project).isDumb && !lesson.properties.canStartInDumbMode
      if (cantBeOpenedInDumb && !LessonManager.instance.lessonShouldBeOpenedCompleted(lesson)) {
        val balloon = createBalloon(LearnBundle.message("indexing.message"))
        balloon.showInCenterOf(name)
        return@l
      }
      CourseManager.instance.openLesson(project, lesson, LessonStartingWay.LEARN_TAB)
    }
    val result = LearningItemPanel(clickAction)
    result.layout = BoxLayout(result, BoxLayout.X_AXIS)
    result.alignmentX = LEFT_ALIGNMENT
    result.border = EmptyBorder(JBUI.scale(7), JBUI.scale(7), JBUI.scale(6), JBUI.scale(7))
    val checkmarkIconLabel = createLabelIcon(if (lesson.passed) FeaturesTrainerIcons.Img.GreenCheckmark else EmptyIcon.ICON_16)
    result.add(createLabelIcon(EmptyIcon.ICON_16))
    result.add(scaledRigid(UISettings.getInstance().expandAndModuleGap, 0))
    result.add(checkmarkIconLabel)

    result.add(rigid(4, 0))
    result.add(name)
    if (iftPluginIsUsing && lesson.isNewLesson()) {
      result.add(rigid(10, 0))
      result.add(NewContentLabel())
    }
    result.add(Box.createHorizontalGlue())

    return result
  }

  private fun createModuleItem(module: IftModule): JPanel {
    val modulePanel = JPanel()
    modulePanel.isOpaque = true
    modulePanel.layout = BoxLayout(modulePanel, BoxLayout.Y_AXIS)
    modulePanel.alignmentY = TOP_ALIGNMENT
    modulePanel.background = Color(0, 0, 0, 0)

    val clickAction: () -> Unit = {
      if (expanded.contains(module)) {
        expanded.remove(module)
      }
      else {
        expanded.clear()
        expanded.add(module)
      }
      updateItems()
    }
    val result = LearningItemPanel(clickAction)
    result.background = UISettings.getInstance().backgroundColor
    result.layout = BoxLayout(result, BoxLayout.X_AXIS)
    result.border = EmptyBorder(JBUI.scale(8), JBUI.scale(7), JBUI.scale(10), JBUI.scale(7))
    result.alignmentX = LEFT_ALIGNMENT

    val expandPanel = JPanel().also {
      it.layout = BoxLayout(it, BoxLayout.Y_AXIS)
      it.isOpaque = false
      it.background = Color(0, 0, 0, 0)
      it.alignmentY = TOP_ALIGNMENT
      it.add(rigid(0, 1))
      val rawIcon = if (expanded.contains(module)) UIUtil.getTreeExpandedIcon() else UIUtil.getTreeCollapsedIcon()
      it.add(createLabelIcon(rawIcon))
    }
    result.add(expandPanel)

    val name = JLabel(module.name)
    name.font = UISettings.getInstance().modulesFont
    if (!iftPluginIsUsing || expanded.contains(module) || !module.lessons.any { it.isNewLesson() }) {
      modulePanel.add(name)
    } else {
      val nameLine = JPanel()
      nameLine.isOpaque = false
      nameLine.layout = BoxLayout(nameLine, BoxLayout.X_AXIS)
      nameLine.alignmentX = LEFT_ALIGNMENT

      nameLine.add(name)
      nameLine.add(rigid(10, 0))
      nameLine.add(NewContentLabel())

      modulePanel.add(nameLine)
    }
    modulePanel.add(scaledRigid(0, UISettings.getInstance().progressModuleGap))

    if (expanded.contains(module)) {
      modulePanel.add(JLabel("<html>${module.description}</html>").also {
        it.font = UISettings.getInstance().getFont(-1)
        it.foreground = UIUtil.getLabelForeground()
      })
    }
    else {
      modulePanel.add(createModuleProgressLabel(module))
    }

    result.add(scaledRigid(UISettings.getInstance().expandAndModuleGap, 0))
    result.add(modulePanel)
    result.add(Box.createHorizontalGlue())
    return result
  }

  private fun createLabelIcon(rawIcon: Icon): JLabel = JLabel(IconUtil.toSize(rawIcon, JBUI.scale(16), JBUI.scale(16)))

  private fun createModuleProgressLabel(module: IftModule): JBLabel {
    val progressStr = learningProgressString(module.lessons)
    val progressLabel = JBLabel(progressStr)
    progressLabel.name = "progressLabel"
    val hasNotPassedLesson = module.lessons.any { !it.passed }
    progressLabel.foreground = if (hasNotPassedLesson) UISettings.getInstance().moduleProgressColor else UISettings.getInstance().completedColor
    progressLabel.font = UISettings.getInstance().getFont(-1)
    progressLabel.alignmentX = LEFT_ALIGNMENT
    return progressLabel
  }
}

private class LearningItemPanel(clickAction: () -> Unit) : JPanel() {
  init {
    isOpaque = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!visibleRect.contains(e.point)) return
        clickAction()
      }

      override fun mouseEntered(e: MouseEvent) {
        repaint()
      }

      override fun mouseExited(e: MouseEvent) {
        repaint()
      }
    })
  }

  override fun paint(g: Graphics) {
    if (mousePosition != null) {
      val g2 = g.create() as Graphics2D
      g2.color = HOVER_COLOR
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      g2.fillRoundRect(0, 0, size.width, size.height, JBUI.scale(5), JBUI.scale(5))
    }
    super.paint(g)
  }
}
