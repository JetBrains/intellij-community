// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.statistic.StatisticBase
import training.ui.*
import training.util.getNextLessonForCurrent
import training.util.getPreviousLessonForCurrent
import training.util.openLinkInBrowser
import training.util.wrapWithUrlPanel
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import kotlin.math.max

internal class LearnPanel(val learnToolWindow: LearnToolWindow) : JPanel() {
  private val lessonPanel = JPanel()

  private val moduleNameLabel: JLabel = LinkLabelWithBackArrow<Any> { _, _ ->
    StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.OPEN_MODULES)
    LessonManager.instance.stopLesson()
    LearningUiManager.resetModulesView()
  }

  private val lessonNameLabel = JLabel() //Name of the current lesson
  val lessonMessagePane = LessonMessagePane()
  private val buttonPanel = JPanel()
  private val nextButton = JButton()
  private val prevButton = JButton()

  private val footer = JPanel()

  private val lessonPanelBoxLayout = BoxLayout(lessonPanel, BoxLayout.Y_AXIS)

  internal var scrollToNewMessages = true

  init {
    isFocusable = false
    background = UISettings.instance.backgroundColor
  }

  fun reinitMe(lesson: Lesson) {
    scrollToNewMessages = true
    clearMessages()
    footer.removeAll()
    lessonPanel.removeAll()
    removeAll()

    layout = BorderLayout()
    isOpaque = true

    initLessonPanel()
    lessonPanel.alignmentX = LEFT_ALIGNMENT
    add(lessonPanel, BorderLayout.CENTER)

    if (lesson.helpLinks.isNotEmpty() && Registry.`is`("ift.help.links", false)) {
      initFooterPanel(lesson)
      add(footer, BorderLayout.PAGE_END)
    }

    preferredSize = Dimension(UISettings.instance.width, 100)
    with (UISettings.instance) {
      border = EmptyBorder(northInset, JBUI.scale(18), southInset, JBUI.scale(18))
    }
  }

  private fun initFooterPanel(lesson: Lesson) {
    val shiftedFooter = JPanel()
    shiftedFooter.name = "footerLessonPanel"
    shiftedFooter.layout = BoxLayout(shiftedFooter, BoxLayout.Y_AXIS)
    shiftedFooter.isFocusable = false
    shiftedFooter.isOpaque = false
    shiftedFooter.border = MatteBorder(JBUI.scale(1), 0, 0, 0, UISettings.instance.separatorColor)

    val footerContent = JPanel()
    footerContent.isOpaque = false
    footerContent.layout = VerticalLayout(5)
    footerContent.add(JLabel(IdeBundle.message("welcome.screen.learnIde.help.and.resources.text")).also {
      it.font = UISettings.instance.getFont(1).deriveFont(Font.BOLD)
    })
    for (helpLink in lesson.helpLinks) {
      val text = helpLink.key
      val link = helpLink.value
      val linkLabel = LinkLabel<Any>(text, null) { _, _ ->
        openLinkInBrowser(link)
      }
      footerContent.add(linkLabel.wrapWithUrlPanel())
    }

    shiftedFooter.add(footerContent)
    shiftedFooter.add(Box.createHorizontalGlue())

    footer.add(shiftedFooter)
    footer.isOpaque = false
    footer.layout = BoxLayout(footer, BoxLayout.Y_AXIS)
    footer.border = UISettings.instance.checkmarkShiftBorder
  }

  private fun initLessonPanel() {
    lessonPanel.name = "lessonPanel"
    lessonPanel.layout = lessonPanelBoxLayout
    lessonPanel.isFocusable = false
    lessonPanel.isOpaque = false

    moduleNameLabel.name = "moduleNameLabel"
    moduleNameLabel.font = UISettings.instance.getFont(1)
    moduleNameLabel.isFocusable = false
    moduleNameLabel.border = UISettings.instance.checkmarkShiftBorder

    lessonNameLabel.name = "lessonNameLabel"
    lessonNameLabel.border = UISettings.instance.checkmarkShiftBorder
    lessonNameLabel.font = UISettings.instance.getFont(5).deriveFont(Font.BOLD)
    lessonNameLabel.alignmentX = Component.LEFT_ALIGNMENT
    lessonNameLabel.isFocusable = false

    lessonMessagePane.name = "lessonMessagePane"
    lessonMessagePane.isFocusable = false
    lessonMessagePane.isOpaque = false
    lessonMessagePane.alignmentX = Component.LEFT_ALIGNMENT
    lessonMessagePane.margin = JBUI.emptyInsets()
    lessonMessagePane.border = EmptyBorder(0, 0, JBUI.scale(24), JBUI.scale(21))
    lessonMessagePane.maximumSize = Dimension(UISettings.instance.width, 10000)

    //Set Next Button UI
    listOf(nextButton, prevButton).forEach {
      it.margin = JBUI.emptyInsets()
      it.isFocusable = false
      it.isVisible = true
      it.isEnabled = true
      it.isOpaque = false
    }

    buttonPanel.name = "buttonPanel"
    buttonPanel.border = EmptyBorder(0, UISettings.instance.checkIndent - JButton().insets.left, 0, 0)
    buttonPanel.isOpaque = false
    buttonPanel.isFocusable = false
    buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
    buttonPanel.alignmentX = Component.LEFT_ALIGNMENT
    updateNavigationButtons()

    //shift right for checkmark
    lessonPanel.add(moduleNameLabel)
    lessonPanel.add(Box.createVerticalStrut(JBUI.scale(20)))
    lessonPanel.add(lessonNameLabel)
    lessonPanel.add(lessonMessagePane)
    lessonPanel.add(buttonPanel)
    lessonPanel.add(Box.createVerticalGlue())
  }

  fun setLessonName(@Nls lessonName: String) {
    lessonNameLabel.text = lessonName
    lessonNameLabel.foreground = UISettings.instance.defaultTextColor
    lessonNameLabel.isFocusable = false
    this.revalidate()
    this.repaint()
  }

  fun setModuleName(@Nls moduleName: String) {
    moduleNameLabel.text = "    $moduleName"
    moduleNameLabel.foreground = UISettings.instance.defaultTextColor
    moduleNameLabel.isFocusable = false
    this.revalidate()
    this.repaint()
  }

  fun addMessage(@Language("HTML") text: String, properties: LessonMessagePane.MessageProperties = LessonMessagePane.MessageProperties()) {
    val messages = MessageFactory.convert(text)
    MessageFactory.setLinksHandlers(learnToolWindow.project, messages)
    addMessages(messages, properties)
  }

  fun addMessages(messageParts: List<MessagePart>, properties: LessonMessagePane.MessageProperties = LessonMessagePane.MessageProperties()) {
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

    val y = needToShow.y + lessonMessagePane.bounds.y + lessonPanel.bounds.y
    if (scrollToNewMessages) {
      adjustMessagesArea()
      val visibleSize = visibleRect.size
      val needToScroll = max(0, y - visibleSize.height/2)
      learnToolWindow.scrollTo(needToScroll)
    }
  }

  /** This important magic method is needed for [getPreferredSize]: it calculates the `lessonPanel.minimumSize`  */
  private fun adjustMessagesArea() {
    //invoke #getPreferredSize explicitly to update actual size of LessonMessagePane
    lessonMessagePane.preferredSize

    //Pack lesson panel
    lessonPanel.repaint()
    //run to update LessonMessagePane.getMinimumSize and LessonMessagePane.getPreferredSize
    lessonPanelBoxLayout.invalidateLayout(lessonPanel)
    lessonPanelBoxLayout.layoutContainer(lessonPanel)
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
    lessonNameLabel.icon = null
    lessonMessagePane.clear()
  }

  private fun updateNavigationButtons() {
    buttonPanel.removeAll()
    rootPane?.defaultButton = null

    updateButton(prevButton, getPreviousLessonForCurrent(), LearnBundle.message("learn.new.ui.button.back"))

    val nextLesson = getNextLessonForCurrent()
    updateButton(nextButton, nextLesson, LearnBundle.message("learn.new.ui.button.next", nextLesson?.name ?: ""))
  }


  private fun updateButton(button: JButton, targetLesson: Lesson?, @Nls buttonText: String) {
    button.isVisible = targetLesson != null
    if (targetLesson != null) {
      button.action = object : AbstractAction() {
        override fun actionPerformed(actionEvent: ActionEvent) {
          StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.OPEN_NEXT_OR_PREV_LESSON)
          CourseManager.instance.openLesson(learnToolWindow.project, targetLesson)
        }
      }
      button.text = buttonText
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

      buttonPanel.add(button)
    }
  }

  @NlsSafe
  private fun getNextLessonKeyStrokeText() = "Enter"

  /** It is a magic implementation and need to invoke [adjustMessagesArea] before the use of this method (from Swing library code) */
  override fun getPreferredSize(): Dimension {
    if (lessonPanel.minimumSize == null) return Dimension(10, 10)
    return Dimension(lessonPanel.minimumSize.getWidth().toInt() + UISettings.instance.westInset + UISettings.instance.eastInset,
                     lessonPanel.minimumSize.getHeight().toInt() + footer.minimumSize.getHeight().toInt() + UISettings.instance.northInset + UISettings.instance.southInset)
  }

  fun makeNextButtonSelected() {
    rootPane?.defaultButton = nextButton
    nextButton.isSelected = true
    nextButton.isFocusable = true
    nextButton.requestFocus()
    if (scrollToNewMessages) {
      adjustMessagesArea()
      learnToolWindow.scrollToTheEnd()
    }
  }

  fun clearRestoreMessage() {
    val needToShow = lessonMessagePane.clearRestoreMessages()
    scrollToMessage(needToShow())
  }

  fun removeMessage(index: Int) {
    lessonMessagePane.removeMessage(index)
  }

  class LinkLabelWithBackArrow<T>(linkListener: LinkListener<T>) : LinkLabel<T>("", null, linkListener) {

    init {
      font = UIUtil.getLabelFont()
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
}
