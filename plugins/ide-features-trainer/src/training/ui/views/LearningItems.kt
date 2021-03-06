// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.HeightLimitedPane
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import org.jetbrains.annotations.NotNull
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.course.IftModule
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.ui.UISettings
import training.util.createBalloon
import training.util.learningProgressString
import training.util.rigid
import training.util.scaledRigid
import java.awt.*
import java.awt.Cursor
import java.awt.event.*
import javax.swing.*
import javax.swing.border.EmptyBorder

private val HOVER_COLOR: Color get() = JBColor.namedColor("Plugins.hoverBackground", JBColor(0xEDF6FE, 0x464A4D))

class LearningItems : JPanel() {
  var modules: List<IftModule> = emptyList()
  private val expanded: MutableSet<IftModule> = mutableSetOf()

  init {
    name = "learningItems"
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
    isFocusable = false
  }

  fun updateItems(showModule: IftModule? = null) {
    if (showModule != null) expanded.add(showModule)

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
    result.layout = BoxLayout(result, BoxLayout.X_AXIS)
    result.alignmentX = LEFT_ALIGNMENT
    result.border = EmptyBorder(JBUI.scale(7), JBUI.scale(7), JBUI.scale(6), JBUI.scale(7))
    val checkmarkIconLabel = createLabelIcon(if (lesson.passed) FeaturesTrainerIcons.Img.GreenCheckmark else EmptyIcon.ICON_16)
    result.add(createLabelIcon(EmptyIcon.ICON_16))
    result.add(scaledRigid(UISettings.instance.expandAndModuleGap, 0))
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
    result.add(rigid(4, 0))
    result.add(name)
    return result
  }

  private fun createModuleItem(module: IftModule): JPanel {
    val modulePanel = JPanel()
    modulePanel.isOpaque = false
    modulePanel.layout = BoxLayout(modulePanel, BoxLayout.Y_AXIS)
    modulePanel.alignmentY = TOP_ALIGNMENT
    modulePanel.background = Color(0, 0, 0, 0)

    val result = object : JPanel() {
      private var onInstall = true

      override fun paint(g: Graphics?) {
        if (onInstall && mouseAlreadyInside(this)) {
          setCorrectBackgroundOnInstall(this)
          onInstall = false
        }
        super.paint(g)
      }
    }
    result.isOpaque = true
    result.background = UISettings.instance.backgroundColor
    result.layout = BoxLayout(result, BoxLayout.X_AXIS)
    result.border = EmptyBorder(JBUI.scale(8), JBUI.scale(7), JBUI.scale(10), JBUI.scale(7))
    result.alignmentX = LEFT_ALIGNMENT

    val mouseAdapter = object : MouseAdapter() {
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
        setCorrectBackgroundOnInstall(result)
        result.revalidate()
        result.repaint()
      }

      override fun mouseExited(e: MouseEvent) {
        result.background = UISettings.instance.backgroundColor
        result.cursor = Cursor.getDefaultCursor()
        result.revalidate()
        result.repaint()
      }
    }

    val expandPanel = JPanel().also {
      it.layout = BoxLayout(it, BoxLayout.Y_AXIS)
      it.isOpaque = false
      it.background = Color(0, 0, 0, 0)
      it.alignmentY = TOP_ALIGNMENT
      //it.add(Box.createVerticalStrut(JBUI.scale(1)))
      it.add(rigid(0, 1))
      val rawIcon = if (expanded.contains(module)) UIUtil.getTreeExpandedIcon() else UIUtil.getTreeCollapsedIcon()
      it.add(createLabelIcon(rawIcon))
    }
    result.add(expandPanel)

    val name = JLabel(module.name)
    name.font = UISettings.instance.modulesFont
    modulePanel.add(name)
    scaledRigid(UISettings.instance.progressModuleGap, 0)
    modulePanel.add(scaledRigid(0, UISettings.instance.progressModuleGap))

    if (expanded.contains(module)) {
      modulePanel.add(HeightLimitedPane(module.description ?: "", -1, UIUtil.getLabelForeground() as JBColor).also {
        it.addMouseListener(mouseAdapter)
      })
    }
    else {
      modulePanel.add(createModuleProgressLabel(module))
    }

    result.add(scaledRigid(UISettings.instance.expandAndModuleGap, 0))
    result.add(modulePanel)
    result.add(Box.createHorizontalGlue())
    result.addMouseListener(mouseAdapter)
    return result
  }

  private fun createLabelIcon(rawIcon: @NotNull Icon): JLabel = JLabel(IconUtil.toSize(rawIcon, JBUI.scale(16), JBUI.scale(16)))

  private fun createModuleProgressLabel(module: IftModule): JBLabel {
    val progressStr = learningProgressString(module.lessons)
    val progressLabel = JBLabel(progressStr)
    progressLabel.name = "progressLabel"
    val hasNotPassedLesson = module.lessons.any { !it.passed }
    progressLabel.foreground = if (hasNotPassedLesson) UISettings.instance.moduleProgressColor else UISettings.instance.completedColor
    progressLabel.font = UISettings.instance.getFont(-1)
    progressLabel.alignmentX = LEFT_ALIGNMENT
    return progressLabel
  }

  private fun setCorrectBackgroundOnInstall(component: Component) {
    component.background = HOVER_COLOR
    component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
  }

  private fun mouseAlreadyInside(c: Component): Boolean {
    val mousePos: Point = MouseInfo.getPointerInfo().location
    val bounds: Rectangle = c.bounds
    bounds.location = c.locationOnScreen
    return bounds.contains(mousePos)
  }
}
