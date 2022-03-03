// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui.views

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.CloseProjectWindowHelper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.VerticalBox
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.intellij.lang.annotations.Language
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.statistic.LessonStartingWay
import training.statistic.StatisticBase
import training.ui.*
import training.util.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import kotlin.math.max

internal class LearnPanel(val learnToolWindow: LearnToolWindow) : JPanel() {
  private val lessonPanel = JPanel()
  val lessonMessagePane = LessonMessagePane()
  private var nextButton: JButton? = null

  private val scrollPane = JBScrollPane(lessonPanel)
  private val stepAnimator = StepAnimator(scrollPane.verticalScrollBar, lessonMessagePane)

  private val lessonPanelBoxLayout = BoxLayout(lessonPanel, BoxLayout.Y_AXIS)

  internal var scrollToNewMessages = true

  init {
    isFocusable = false
    background = UISettings.instance.backgroundColor
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = true

    scrollPane.addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updatePanelSize(getVisibleAreaWidth())
      }
    })
  }

  fun reinitMe(lesson: Lesson) {
    with(UISettings.instance) {
      border = EmptyBorder(northInset, 0, southInset, 0)
    }

    scrollToNewMessages = true
    clearMessages()
    lessonPanel.removeAll()
    removeAll()

    add(createHeaderPanel(lesson))
    add(createSmallSeparator())
    add(scaledRigid(UISettings.instance.panelWidth, JBUI.scale(4)))
    initLessonPanel(lesson)
    scrollPane.alignmentX = LEFT_ALIGNMENT
    add(scrollPane)
  }

  fun updatePanelSize(viewAreaWidth: Int) {
    val width = max(UISettings.instance.panelWidth, viewAreaWidth) - 2 * UISettings.instance.learnPanelSideOffset
    lessonMessagePane.preferredSize = null
    lessonMessagePane.setBounds(0, 0, width, 10000)
    lessonMessagePane.revalidate()
    lessonMessagePane.repaint()
    lessonMessagePane.preferredSize = Dimension(width, lessonMessagePane.preferredSize.height)

    lessonPanel.revalidate()
    lessonPanel.doLayout() // need to reset correct location for auto-scroll
    lessonPanel.repaint()
  }

  private fun createFooterPanel(lesson: Lesson): JPanel {
    val shiftedFooter = JPanel()
    shiftedFooter.name = "footerLessonPanel"
    shiftedFooter.layout = BoxLayout(shiftedFooter, BoxLayout.Y_AXIS)
    shiftedFooter.isFocusable = false
    shiftedFooter.isOpaque = false
    shiftedFooter.border = MatteBorder(JBUI.scale(1), 0, 0, 0, UISettings.instance.separatorColor)

    val footerContent = JPanel()
    footerContent.isOpaque = false
    footerContent.layout = BoxLayout(footerContent, BoxLayout.Y_AXIS)
    footerContent.add(rigid(0, 16))
    footerContent.add(JLabel(IdeBundle.message("welcome.screen.learnIde.help.and.resources.text")).also {
      it.font = UISettings.instance.getFont(1).deriveFont(Font.BOLD)
    })
    for (helpLink in lesson.helpLinks) {
      val text = helpLink.key
      val link = helpLink.value
      val linkLabel = LinkLabel<Any>(text, null) { _, _ ->
        openLinkInBrowser(link)
        StatisticBase.logHelpLinkClicked(lesson.id)
      }
      footerContent.add(rigid(0, 5))
      footerContent.add(linkLabel.wrapWithUrlPanel())
    }

    shiftedFooter.add(footerContent)
    shiftedFooter.add(Box.createHorizontalGlue())

    val footer = JPanel()
    footer.add(shiftedFooter)
    footer.alignmentX = Component.LEFT_ALIGNMENT
    footer.isOpaque = false
    footer.layout = BoxLayout(footer, BoxLayout.Y_AXIS)
    footer.border = UISettings.instance.lessonHeaderBorder
    return footer
  }

  private fun initLessonPanel(lesson: Lesson) {
    lessonPanel.name = "lessonPanel"
    lessonPanel.layout = lessonPanelBoxLayout
    lessonPanel.isFocusable = false
    lessonPanel.isOpaque = true
    with(UISettings.instance) {
      lessonPanel.background = backgroundColor
      lessonPanel.border = EmptyBorder(0, learnPanelSideOffset, 0, learnPanelSideOffset)
    }

    lessonMessagePane.name = "lessonMessagePane"
    lessonMessagePane.isFocusable = false
    lessonMessagePane.isOpaque = false
    lessonMessagePane.alignmentX = Component.LEFT_ALIGNMENT
    lessonMessagePane.margin = JBInsets.emptyInsets()
    lessonMessagePane.border = EmptyBorder(0, 0, JBUI.scale(20), JBUI.scale(14))

    lessonPanel.add(scaledRigid(UISettings.instance.panelWidth - 2 * UISettings.instance.learnPanelSideOffset, JBUI.scale(19)))
    lessonPanel.add(createLessonNameLabel(lesson))
    lessonPanel.add(lessonMessagePane)
    lessonPanel.add(createButtonsPanel())
    lessonPanel.add(Box.createVerticalGlue())

    if (lesson.helpLinks.isNotEmpty() && Registry.`is`("ift.help.links", false)) {
      lessonPanel.add(rigid(0, 16))
      lessonPanel.add(createFooterPanel(lesson))
    }
  }

  private fun createLessonNameLabel(lesson: Lesson): JLabel {
    val lessonNameLabel = JLabel()
    lessonNameLabel.name = "lessonNameLabel"
    lessonNameLabel.border = UISettings.instance.lessonHeaderBorder
    lessonNameLabel.font = UISettings.instance.getFont(5).deriveFont(Font.BOLD)
    lessonNameLabel.alignmentX = Component.LEFT_ALIGNMENT
    lessonNameLabel.isFocusable = false
    lessonNameLabel.text = lesson.name
    lessonNameLabel.foreground = UISettings.instance.defaultTextColor
    return lessonNameLabel
  }

  private fun createHeaderPanel(lesson: Lesson): VerticalBox {
    val moduleNameLabel: JLabel = LinkLabelWithBackArrow<Any> { _, _ ->
      StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.OPEN_MODULES)
      LessonManager.instance.stopLesson()
      LearningUiManager.resetModulesView()
    }
    moduleNameLabel.name = "moduleNameLabel"
    moduleNameLabel.font = UISettings.instance.getFont(1)
    moduleNameLabel.text = "    ${lesson.module.name}"
    moduleNameLabel.foreground = UISettings.instance.defaultTextColor
    moduleNameLabel.isFocusable = false

    val linksPanel = JPanel()
    linksPanel.isOpaque = false
    linksPanel.layout = BoxLayout(linksPanel, BoxLayout.X_AXIS)
    linksPanel.alignmentX = LEFT_ALIGNMENT
    linksPanel.border = EmptyBorder(0, 0, JBUI.scale(12), 0)

    linksPanel.add(moduleNameLabel)
    linksPanel.add(Box.createHorizontalGlue())

    val exitLink = JLabel(LearnBundle.message("exit.learning.link"), AllIcons.Actions.Exit, SwingConstants.LEADING)
    exitLink.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    exitLink.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!StatisticBase.isLearnProjectCloseLogged) {
          StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.EXIT_LINK)
        }
        LessonManager.instance.stopLesson()
        val langSupport = LangManager.getInstance().getLangSupport()
        val project = learnToolWindow.project
        langSupport?.onboardingFeedbackData?.let {
          showOnboardingLessonFeedbackForm(project, it, false)
          langSupport.onboardingFeedbackData = null
        }

        if (langSupport != null && isLearningProject(project, langSupport)) {
          CloseProjectWindowHelper().windowClosing(project)
        } else {
          LearningUiManager.resetModulesView()
          ToolWindowManager.getInstance(project).getToolWindow(LearnToolWindowFactory.LEARN_TOOL_WINDOW)?.hide()
        }
      }
    })
    linksPanel.add(exitLink)

    val headerPanel = VerticalBox()
    headerPanel.isOpaque = false
    headerPanel.alignmentX = LEFT_ALIGNMENT
    headerPanel.border = UISettings.instance.lessonHeaderBorder
    headerPanel.add(linksPanel)
    return headerPanel
  }

  fun addMessage(@Language("HTML") text: String, properties: LessonMessagePane.MessageProperties = LessonMessagePane.MessageProperties()) {
    val messages = MessageFactory.convert(text)
    MessageFactory.setLinksHandlers(messages)
    addMessages(messages, properties)
  }

  fun addMessages(messageParts: List<MessagePart>,
                  properties: LessonMessagePane.MessageProperties = LessonMessagePane.MessageProperties()) {
    val needToShow = lessonMessagePane.addMessage(messageParts, properties)
    adjustMessagesArea()
    if (properties.state != LessonMessagePane.MessageState.INACTIVE) {
      scrollToMessage(needToShow())
    }
  }

  fun focusCurrentMessage() {
    scrollToMessage(lessonMessagePane.getCurrentMessageRectangle())
  }

  private fun scrollToMessage(needToShow: Rectangle?) {
    if (needToShow == null) return

    if (scrollToNewMessages) {
      adjustMessagesArea()
      val visibleSize = lessonPanel.visibleRect.size
      val needToScroll = max(0, needToShow.y + lessonMessagePane.location.y - visibleSize.height / 2 + needToShow.height / 2)
      scrollTo(needToScroll)
    }
  }

  fun adjustMessagesArea() {
    updatePanelSize(getVisibleAreaWidth())
    revalidate()
    repaint()
  }

  fun resetMessagesNumber(number: Int) {
    val needToShow = lessonMessagePane.resetMessagesNumber(number)
    adjustMessagesArea()
    scrollToMessage(needToShow())
  }

  fun removeInactiveMessages(number: Int) {
    lessonMessagePane.removeInactiveMessages(number)
    adjustMessagesArea() // TODO: fix it
  }

  fun messagesNumber(): Int = lessonMessagePane.messagesNumber()

  fun setPreviousMessagesPassed() {
    lessonMessagePane.passPreviousMessages()
    adjustMessagesArea()
  }

  private fun clearMessages() {
    lessonMessagePane.clear()
  }

  private fun createButtonsPanel(): JPanel {
    val buttonPanel = JPanel()
    buttonPanel.name = "buttonPanel"
    buttonPanel.border = EmptyBorder(0, UISettings.instance.checkIndent - JButton().insets.left, 0, 0)
    buttonPanel.isOpaque = false
    buttonPanel.isFocusable = false
    buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
    buttonPanel.alignmentX = Component.LEFT_ALIGNMENT
    rootPane?.defaultButton = null

    val previousLesson = getPreviousLessonForCurrent()
    val prevButton = previousLesson?.let { createNavigationButton(previousLesson, isNext = false) }

    val nextLesson = getNextLessonForCurrent()
    nextButton = nextLesson?.let { createNavigationButton(nextLesson, isNext = true) }

    for (button in listOfNotNull(prevButton, nextButton)) {
      button.margin = JBInsets.emptyInsets()
      button.isFocusable = false
      button.isEnabled = true
      button.isOpaque = false
      buttonPanel.add(button)
    }
    return buttonPanel
  }

  private fun createNavigationButton(targetLesson: Lesson, isNext: Boolean): JButton {
    val button = JButton()
    button.action = object : AbstractAction() {
      override fun actionPerformed(actionEvent: ActionEvent) {
        StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.OPEN_NEXT_OR_PREV_LESSON)
        val startingWay = if (isNext) LessonStartingWay.NEXT_BUTTON else LessonStartingWay.PREV_BUTTON
        CourseManager.instance.openLesson(learnToolWindow.project, targetLesson, startingWay)
      }
    }
    button.text = if (isNext) {
      LearnBundle.message("learn.new.ui.button.next", targetLesson.name)
    }
    else LearnBundle.message("learn.new.ui.button.back")
    button.updateUI()
    button.isSelected = true

    if (!targetLesson.passed &&
        !targetLesson.properties.canStartInDumbMode &&
        DumbService.getInstance(learnToolWindow.project).isDumb) {
      button.isEnabled = false
      button.toolTipText = LearnBundle.message("indexing.message")
      button.isSelected = false
      DumbService.getInstance(learnToolWindow.project).runWhenSmart {
        button.isEnabled = true
        button.toolTipText = ""
        button.isSelected = true
      }
    }
    return button
  }

  fun makeNextButtonSelected() {
    nextButton?.let {
      rootPane?.defaultButton = it
      it.isSelected = true
      it.isFocusable = true
      it.requestFocus()
    }
    if (scrollToNewMessages) {
      adjustMessagesArea()
      scrollToTheEnd()
    }
  }

  fun clearRestoreMessage() {
    val needToShow = lessonMessagePane.clearRestoreMessages()
    scrollToMessage(needToShow())
  }

  fun removeMessage(index: Int) {
    lessonMessagePane.removeMessage(index)
  }

  private fun getVisibleAreaWidth(): Int {
    val scrollWidth = scrollPane.verticalScrollBar?.size?.width ?: 0
    return scrollPane.viewport.extentSize.width - scrollWidth
  }

  private fun scrollToTheEnd() {
    val vertical = scrollPane.verticalScrollBar
    if (useAnimation()) stepAnimator.startAnimation(vertical.maximum)
    else vertical.value = vertical.maximum
  }

  fun scrollToTheStart() {
    scrollPane.verticalScrollBar.value = 0
  }

  private fun scrollTo(needTo: Int) {
    if (useAnimation()) stepAnimator.startAnimation(needTo)
    else {
      scrollPane.verticalScrollBar.value = needTo
    }
  }

  private fun useAnimation() = Registry.`is`("ift.use.scroll.animation", false)
}

private class LinkLabelWithBackArrow<T>(linkListener: LinkListener<T>) : LinkLabel<T>("", null, linkListener) {
  init {
    font = StartupUiUtil.getLabelFont()
  }

  override fun paint(g: Graphics?) {
    super.paint(g)
    val arrowWingHeight = textBounds.height / 4

    val g2d = g as Graphics2D
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val stroke3: Stroke = BasicStroke(1.2f * font.size / 13, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g2d.stroke = stroke3
    g2d.color = foreground
    g2d.drawLine(textBounds.x, textBounds.y + textBounds.height / 2,
                 textBounds.x + 5 * textBounds.height / 17, textBounds.y + textBounds.height / 2 - arrowWingHeight)
    g2d.drawLine(textBounds.x, textBounds.y + textBounds.height / 2,
                 textBounds.x + 9 * textBounds.height / 17, textBounds.y + textBounds.height / 2)
    g2d.drawLine(textBounds.x, textBounds.y + textBounds.height / 2,
                 textBounds.x + 5 * textBounds.height / 17, textBounds.y + textBounds.height / 2 + arrowWingHeight)
  }
}

private fun createSmallSeparator(): Component {
  // Actually standard JSeparator can be used, but it adds some extra size for Y and I don't know how to fix it :(
  val separatorPanel = JPanel()
  separatorPanel.isOpaque = false
  separatorPanel.layout = BoxLayout(separatorPanel, BoxLayout.X_AXIS)
  separatorPanel.add(Box.createHorizontalGlue())
  separatorPanel.border = MatteBorder(0, 0, JBUI.scale(1), 0, UISettings.instance.separatorColor)
  return separatorPanel
}
