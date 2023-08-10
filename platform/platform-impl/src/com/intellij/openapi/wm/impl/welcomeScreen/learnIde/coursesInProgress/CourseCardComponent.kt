// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy.JBAcademyWelcomeScreenBundle
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*


private const val CARD_GAP = 7
private const val CARD_WIDTH = 80
private const val CARD_HEIGHT = 55
private const val LOGO_SIZE = 20

private const val INFO_HGAP = 0
private const val INFO_VGAP = 3


private val hoverColor: Color = ListPluginComponent.SELECTION_COLOR
private val infoForeground: Color = NamedColorUtil.getInactiveTextColor()
val mainBackgroundColor: Color = WelcomeScreenUIManager.getMainAssociatedComponentBackground()

class CourseCardComponent(val data: CourseInfo) : Wrapper() {
  lateinit var actionComponent: JComponent
  private lateinit var baseComponent: JComponent
  private var isHovered: Boolean = false

  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    border = JBUI.Borders.empty(CARD_GAP)
    preferredSize = JBUI.size(CARD_WIDTH, CARD_HEIGHT)

    val panel = panel {
      row {
        val icon = data.icon
        if (icon != null) {
          cell(getScaledLogoComponent(icon, this@CourseCardComponent))
            .align(AlignY.TOP).gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
        }
        cell(createMainComponent()).align(com.intellij.ui.dsl.builder.Align.FILL)
      }
    }
    setContent(panel)
    updateColors(mainBackgroundColor)
  }

  private fun getScaledLogoComponent(logo: Icon, ancestor: Component): Wrapper {
    val scaleFactor = LOGO_SIZE / logo.iconHeight.toFloat()
    val scaledIcon = IconUtil.scale(logo, ancestor, scaleFactor)
    return Wrapper(JBLabel(IconUtil.toSize(scaledIcon, JBUI.scale(LOGO_SIZE), JBUI.scale(LOGO_SIZE))))
  }

  fun getClickComponent(): Component {
    return baseComponent
  }

  private fun createMainComponent(): JPanel {
    baseComponent = NonOpaquePanel()
    baseComponent.add(CourseNameComponent(data.name), BorderLayout.NORTH)
    baseComponent.add(CourseInfoComponent(data.tasksSolved, data.tasksTotal), BorderLayout.SOUTH)

    val panel = NonOpaquePanel()
    panel.add(baseComponent, BorderLayout.CENTER)

    actionComponent = createSideActionComponent()
    actionComponent.isVisible = false
    panel.add(actionComponent, BorderLayout.LINE_END)

    return panel
  }

  private fun createSideActionComponent(): JComponent {
    val removeLabel = JBLabel(AllIcons.Diff.Remove)

    removeLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        if (showRemoveCourseDialog(data.name) == Messages.NO) {
          val coursesStorages = CoursesStorageProvider.getAllStorages()
          coursesStorages.any {
            it.removeCourseByLocation(data.location)
          }
          ApplicationManager.getApplication().messageBus.syncPublisher(COURSE_DELETED).courseDeleted(data)
        }
      }
    })

    return Wrapper(removeLabel).apply {
      isEnabled = false
      isVisible = false
    }
  }

  private fun showRemoveCourseDialog(courseName: String): Int {
    return Messages.showDialog(null,
                               JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.remove.course.description", courseName),
                               JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.remove.course.title"),
                               arrayOf(
                                 Messages.getCancelButton(),
                                 JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.remove.course.title")
                               ),
                               Messages.OK,
                               Messages.getErrorIcon())
  }

  fun updateColors(background: Color) {
    UIUtil.setBackgroundRecursively(this, background)
    repaint()
  }

  fun onHover() {
    isHovered = true
    updateColors(hoverColor)
    setActionComponentVisible(true)
  }

  fun onHoverEnded() {
    isHovered = false
    updateColors(mainBackgroundColor)
    setActionComponentVisible(false)
  }
  private fun setActionComponentVisible(visible: Boolean) {
    actionComponent.isVisible = visible
    actionComponent.isEnabled = visible
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = background
    val cornerRadius = 20
    g2.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
    g2.color = if (isHovered) hoverColor else mainBackgroundColor
    g2.drawRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
    g2.dispose()
  }
}

private class CourseNameComponent(@NonNls name: String) : JPanel(BorderLayout()) {

  init {
    val nameLabel = JLabel().apply {
      text = name
      font = this.font.deriveFont(Font.BOLD)
    }

    add(nameLabel, BorderLayout.CENTER)
  }
}

private class CourseInfoComponent(
  solvedTasksNumber: Int,
  totalTaskNumber: Int
) : JPanel(FlowLayout(FlowLayout.LEFT, INFO_HGAP, INFO_VGAP)) {

  init {

    // TODO: should we add special view for edutools course creator
    val progressBar = createProgressBar().apply {
      border = JBUI.Borders.emptyRight(CARD_GAP)
      maximum = totalTaskNumber
      value = solvedTasksNumber
      isVisible = solvedTasksNumber != 0
    }
    add(progressBar)

    val infoComponent = Wrapper(JLabel().apply {
      foreground = infoForeground
      text = when (solvedTasksNumber) {
        0 -> if (totalTaskNumber != 0) JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.courses.card.no.tasks") else ""
        totalTaskNumber -> JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.courses.card.completed")
        else -> "$solvedTasksNumber/$totalTaskNumber"
      }
    }).apply {
      border = JBUI.Borders.emptyTop(5)
    }
    add(Wrapper(infoComponent))
  }

  fun createProgressBar() : JProgressBar {
    val progressBar = JProgressBar()

    progressBar.setUI(object : DarculaProgressBarUI() {
      override fun getRemainderColor(): Color {
        return JBColor(Gray._220, Color(76, 77, 79))
      }
    })
    progressBar.foreground = ColorProgressBar.GREEN
    progressBar.isIndeterminate = false
    progressBar.putClientProperty("ProgressBar.flatEnds", true)
    return progressBar
  }
}
