// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.interfaces.Lesson
import training.learn.lesson.LessonManager
import training.ui.*
import training.util.getNextLessonForCurrent
import training.util.getPreviousLessonForCurrent
import training.util.openLinkInBrowser
import training.util.useNewLearningUi
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

class LearnPanel(private val learnToolWindow: LearnToolWindow) : JPanel() {
  private val lessonPanel = JPanel()

  private val moduleNameLabel: JLabel = if (!useNewLearningUi) JLabel()
  else LinkLabelWithBackArrow<Any> { _, _ ->
    LessonManager.instance.stopLesson()
    LearningUiManager.resetModulesView()
  }

  private val allTopicsLabel: LinkLabel<Any> = LinkLabel(LearnBundle.message("learn.ui.alltopics"), null)

  private val lessonNameLabel = JLabel() //Name of the current lesson
  val lessonMessagePane = LessonMessagePane()
  private val buttonPanel = JPanel()
  private val nextButton = JButton(LearnBundle.message("learn.ui.button.skip"))
  private val prevButton = JButton()

  val modulePanel = ModulePanel()
  private val footer = JPanel()

  private val lessonPanelBoxLayout = BoxLayout(lessonPanel, BoxLayout.Y_AXIS)

  init {
    isFocusable = false
  }

  fun reinitMe(lesson: Lesson) {
    clearMessages()
    modulePanel.removeAll()
    footer.removeAll()
    lessonPanel.removeAll()
    removeAll()

    layout = if (useNewLearningUi) BorderLayout() else BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = true

    initLessonPanel()
    lessonPanel.alignmentX = LEFT_ALIGNMENT
    add(lessonPanel, BorderLayout.CENTER)

    if (!useNewLearningUi) {
      initModulePanel()
      add(modulePanel, BorderLayout.PAGE_END)
    }
    else if (lesson.helpLinks.isNotEmpty() && Registry.`is`("ift.help.links", false)) {
      initFooterPanel(lesson)
      add(footer, BorderLayout.PAGE_END)
    }

    preferredSize = Dimension(UISettings.instance.width, 100)
    border = UISettings.instance.emptyBorder
  }

  private fun initFooterPanel(lesson: Lesson) {
    val shiftedFooter = JPanel()
    shiftedFooter.name = "footerLessonPanel"
    shiftedFooter.layout = BoxLayout(shiftedFooter, BoxLayout.Y_AXIS)
    shiftedFooter.isFocusable = false
    shiftedFooter.isOpaque = false
    shiftedFooter.border = MatteBorder(1, 0, 0, 0, UISettings.instance.separatorColor)

    val footerContent = JPanel()
    footerContent.isOpaque = false
    footerContent.layout = VerticalLayout(5)
    footerContent.add(JLabel(IdeBundle.message("welcome.screen.learnIde.help.and.resources.text")).also {
      it.font = UISettings.instance.helpHeaderFont
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

  private fun LinkLabel<Any>.wrapWithUrlPanel(): JPanel {
    val jPanel = JPanel()
    jPanel.isOpaque = false
    jPanel.layout = BoxLayout(jPanel, BoxLayout.LINE_AXIS)
    jPanel.add(this, BorderLayout.CENTER)
    jPanel.add(JLabel(AllIcons.Ide.External_link_arrow), BorderLayout.EAST)
    jPanel.maximumSize = jPanel.preferredSize
    jPanel.alignmentX = LEFT_ALIGNMENT
    return jPanel
  }

  private fun initLessonPanel() {
    lessonPanel.name = "lessonPanel"
    lessonPanel.layout = lessonPanelBoxLayout
    lessonPanel.isFocusable = false
    lessonPanel.isOpaque = false

    moduleNameLabel.name = "moduleNameLabel"
    moduleNameLabel.font = UISettings.instance.moduleNameFont
    moduleNameLabel.isFocusable = false
    moduleNameLabel.border = UISettings.instance.checkmarkShiftBorder

    allTopicsLabel.name = "allTopicsLabel"
    allTopicsLabel.setListener({ _, _ -> LearningUiManager.resetModulesView() }, null)

    lessonNameLabel.name = "lessonNameLabel"
    lessonNameLabel.border = UISettings.instance.checkmarkShiftBorder
    lessonNameLabel.font = UISettings.instance.lessonHeaderFont
    lessonNameLabel.alignmentX = Component.LEFT_ALIGNMENT
    lessonNameLabel.isFocusable = false

    lessonMessagePane.name = "lessonMessagePane"
    lessonMessagePane.isFocusable = false
    lessonMessagePane.isOpaque = false
    lessonMessagePane.alignmentX = Component.LEFT_ALIGNMENT
    lessonMessagePane.margin = JBUI.emptyInsets()
    lessonMessagePane.border = EmptyBorder(0, 0, 0, 0)
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
    buttonPanel.border = UISettings.instance.checkmarkShiftBorder
    buttonPanel.isOpaque = false
    buttonPanel.isFocusable = false
    buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
    buttonPanel.alignmentX = Component.LEFT_ALIGNMENT
    if (useNewLearningUi) {
      updateNavigationButtons()
    }
    else {
      buttonPanel.add(nextButton)
    }

    //shift right for checkmark
    if (useNewLearningUi) {
      lessonPanel.add(moduleNameLabel)
      lessonPanel.add(UISettings.rigidGap(UISettings::moduleNameLessonGap))
      lessonPanel.add(lessonNameLabel)
      lessonPanel.add(lessonMessagePane)
      //lessonPanel.add(Box.createVerticalStrut(UISettings.instance.beforeButtonGap))
      lessonPanel.add(buttonPanel)
      //lessonPanel.add(Box.createVerticalStrut(UISettings.instance.afterButtonGap))
      lessonPanel.add(Box.createVerticalGlue())
    }
    else {
      lessonPanel.add(moduleNameLabel)
      lessonPanel.add(Box.createVerticalStrut(UISettings.instance.lessonNameGap))
      lessonPanel.add(lessonNameLabel)
      lessonPanel.add(lessonMessagePane)
      lessonPanel.add(Box.createVerticalStrut(UISettings.instance.beforeButtonGap))

      lessonPanel.add(Box.createVerticalGlue())
      lessonPanel.add(buttonPanel)
      lessonPanel.add(Box.createVerticalStrut(UISettings.instance.afterButtonGap))
    }
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

  fun addMessage(@Language("HTML") text: String, state: LessonMessagePane.MessageState = LessonMessagePane.MessageState.NORMAL) {
    val messages = MessageFactory.convert(text)
    MessageFactory.setLinksHandlers(learnToolWindow.project, messages)
    addMessages(messages, state)
  }

  fun addMessages(messageParts: List<MessagePart>, state: LessonMessagePane.MessageState = LessonMessagePane.MessageState.NORMAL) {
    val needToShow = lessonMessagePane.addMessage(messageParts, state)
    adjustMessagesArea()
    if (useNewLearningUi) {
      if (state != LessonMessagePane.MessageState.INACTIVE && needToShow != null) {
        lessonMessagePane.scrollRectToVisible(needToShow)
      }
    }
    else {
      learnToolWindow.scrollToTheEnd()
    }
  }

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
    lessonMessagePane.resetMessagesNumber(number)
    adjustMessagesArea()
  }

  fun removeInactiveMessages(number: Int) {
    lessonMessagePane.removeInactiveMessages(number)
    adjustMessagesArea() // TODO: fix it
  }

  fun messagesNumber(): Int = lessonMessagePane.messagesNumber()

  fun setPreviousMessagesPassed() {
    lessonMessagePane.passPreviousMessages()
  }

  fun setLessonPassed() {
    setButtonToNext()
    revalidate()
    this.repaint()
  }

  private fun setButtonToNext() {
    nextButton.isVisible = true
    lessonPanel.revalidate()
    lessonPanel.repaint()
  }

  fun clearMessages() {
    lessonNameLabel.icon = null
    lessonMessagePane.clear()
  }

  fun setButtonNextAction(notPassedLesson: Lesson?, @Nls text: String?, runnable: () -> Unit) {
    nextButton.action = object : AbstractAction() {
      override fun actionPerformed(actionEvent: ActionEvent) {
        runnable()
      }
    }
    val keyStroke = getNextLessonKeyStrokeText()
    nextButton.text = when {
      text != null -> {
        "$text ($keyStroke)"
      }
      notPassedLesson != null -> {
        "${LearnBundle.message("learn.ui.button.next.lesson")}: ${notPassedLesson.name} ($keyStroke)"
      }
      else -> {
        LearnBundle.message("learn.ui.button.next.lesson") + " ($keyStroke)"
      }
    }
    nextButton.isSelected = true
    rootPane?.defaultButton = nextButton
  }

  private fun updateNavigationButtons() {
    buttonPanel.removeAll()
    rootPane?.defaultButton = null

    val prevLesson = getPreviousLessonForCurrent()
    prevButton.isVisible = prevLesson != null
    if (prevLesson != null) {
      prevButton.action = object : AbstractAction() {
        override fun actionPerformed(actionEvent: ActionEvent) {
          CourseManager.instance.openLesson(learnToolWindow.project, prevLesson)
        }
      }
      prevButton.text = LearnBundle.message("learn.new.ui.button.back")
      prevButton.updateUI()
      prevButton.isSelected = true

      buttonPanel.add(prevButton)
    }

    val nextLesson = getNextLessonForCurrent()
    nextButton.isVisible = nextLesson != null
    if (nextLesson != null) {
      nextButton.action = object : AbstractAction() {
        override fun actionPerformed(actionEvent: ActionEvent) {
          CourseManager.instance.openLesson(learnToolWindow.project, nextLesson)
        }
      }
      nextButton.text = LearnBundle.message("learn.new.ui.button.next", nextLesson.name)
      nextButton.updateUI()
      nextButton.isSelected = true

      buttonPanel.add(nextButton)
    }
  }

  fun updateNextButtonAction(@Nls text: String?, runnable: (() -> Unit)?) {
    rootPane?.defaultButton = null
    nextButton.action = object : AbstractAction() {
      override fun actionPerformed(actionEvent: ActionEvent) {
        runnable?.invoke()
      }
    }
    val keyStroke = getNextLessonKeyStrokeText()
    if (text == null || text.isEmpty()) {
      nextButton.text = "${LearnBundle.message("learn.ui.button.skip")} ($keyStroke)"
      nextButton.updateUI()
    }
    else {
      nextButton.text = "${LearnBundle.message("learn.ui.button.skip.module")}: $text ($keyStroke)"
      nextButton.updateUI()
    }
    nextButton.isSelected = true
    nextButton.isVisible = runnable != null
  }

  @NlsSafe
  private fun getNextLessonKeyStrokeText() = "Enter"

  fun hideNextButton() {
    nextButton.isVisible = false
  }

  private fun initModulePanel() {
    modulePanel.name = LearnPanel::modulePanel.name
    modulePanel.layout = BoxLayout(modulePanel, BoxLayout.Y_AXIS)
    modulePanel.isFocusable = false
    modulePanel.isOpaque = false

    //define separator
    modulePanel.border = MatteBorder(1, 0, 0, 0, UISettings.instance.separatorColor)
    modulePanel.alignmentX = LEFT_ALIGNMENT
  }

  fun updateButtonUi() {
    nextButton.updateUI()
  }

  inner class ModulePanel : JPanel() {
    private val lessonLabelMap = BidirectionalMap<Lesson, MyLinkLabel>()
    private val moduleNamePanel = JPanel() //contains moduleNameLabel and allTopicsLabel

    fun init(lesson: Lesson) {
      val module = lesson.module
      val myLessons = module.lessons

      //create ModuleLessons region
      val moduleLessons = JLabel()
      moduleLessons.name = "moduleLessons"

      moduleNamePanel.name = "moduleNamePanel"
      moduleNamePanel.border = EmptyBorder(UISettings.instance.lessonGap, UISettings.instance.checkIndent, 0, 0)
      moduleNamePanel.isOpaque = false
      moduleNamePanel.isFocusable = false
      moduleNamePanel.layout = BoxLayout(moduleNamePanel, BoxLayout.X_AXIS)
      moduleNamePanel.alignmentX = Component.LEFT_ALIGNMENT
      moduleNamePanel.removeAll()
      moduleNamePanel.add(moduleLessons)
      moduleNamePanel.add(Box.createHorizontalStrut(20))
      allTopicsLabel.alignmentX = Component.CENTER_ALIGNMENT
      moduleNamePanel.add(Box.createHorizontalGlue())
      moduleNamePanel.add(allTopicsLabel)

      moduleLessons.text = lesson.module.name
      moduleLessons.font = UISettings.instance.boldFont
      moduleLessons.isFocusable = false

      add(UISettings.rigidGap(UISettings::moduleNameSeparatorGap))
      add(moduleNamePanel)
      add(UISettings.rigidGap(UISettings::moduleNameLessonsGap))

      buildLessonLabels(lesson, myLessons)
      maximumSize = Dimension(UISettings.instance.width, modulePanel.preferredSize.height)
    }

    private fun buildLessonLabels(lesson: Lesson, myLessons: List<Lesson>) {
      lessonLabelMap.clear()
      for (currentLesson in myLessons) {
        val lessonName = currentLesson.name

        val lessonLinkLabel = MyLinkLabel(lessonName)
        lessonLinkLabel.horizontalTextPosition = SwingConstants.LEFT
        lessonLinkLabel.border = EmptyBorder(0, UISettings.instance.checkIndent, UISettings.instance.lessonGap, 0)
        lessonLinkLabel.isFocusable = false
        lessonLinkLabel.setListener({ _, _ ->
                                      try {
                                        val project = guessCurrentProject(this@LearnPanel)
                                        CourseManager.instance.openLesson(project, currentLesson)
                                      }
                                      catch (e1: Exception) {
                                        LOG.warn(e1)
                                      }
                                    }, null)

        if (lesson == currentLesson) {
          //selected lesson
          lessonLinkLabel.setTextColor(UISettings.instance.lessonActiveColor)
        }
        else {
          lessonLinkLabel.resetTextColor()
        }
        lessonLinkLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        lessonLabelMap[currentLesson] = lessonLinkLabel
        add(lessonLinkLabel)
      }
    }

    fun updateLessons(lesson: Lesson) {
      for ((curLesson, lessonLabel) in lessonLabelMap.entries) {
        if (lesson == curLesson) {
          lessonLabel.setTextColor(UISettings.instance.lessonActiveColor)
        }
        else {
          lessonLabel.resetTextColor()
        }
      }
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      paintModuleCheckmarks(g)
    }

    private fun paintModuleCheckmarks(g: Graphics) {
      for ((lesson, jLabel) in lessonLabelMap.entries) {
        if (lesson.passed) {
          val point = jLabel.location
          if (!SystemInfo.isMac) {
            FeaturesTrainerIcons.Img.Checkmark.paintIcon(this, g, point.x, point.y + 1)
          }
          else {
            FeaturesTrainerIcons.Img.Checkmark.paintIcon(this, g, point.x, point.y + 2)
          }
        }
      }
    }


    internal inner class MyLinkLabel(@Nls text: String) : LinkLabel<Any>(text, null) {

      private var userTextColor: Color? = null

      override fun getTextColor(): Color {
        return userTextColor ?: super.getTextColor()
      }

      fun setTextColor(color: Color) {
        userTextColor = color
      }

      fun resetTextColor() {
        userTextColor = null
      }
    }
  }

  override fun getPreferredSize(): Dimension {
    if (lessonPanel.minimumSize == null) return Dimension(10, 10)
    if (useNewLearningUi) {
      return Dimension(lessonPanel.minimumSize.getWidth().toInt() + UISettings.instance.westInset + UISettings.instance.eastInset,
                       lessonPanel.minimumSize.getHeight().toInt() + footer.minimumSize.getHeight().toInt() + UISettings.instance.northInset + UISettings.instance.southInset)
    }
    return if (modulePanel.minimumSize == null) Dimension(10, 10)
    else Dimension(
      lessonPanel.minimumSize.getWidth().toInt() +
      UISettings.instance.westInset +
      UISettings.instance.eastInset,
      lessonPanel.minimumSize.getHeight().toInt() +
      modulePanel.minimumSize.getHeight().toInt() +
      UISettings.instance.northInset +
      UISettings.instance.southInset)
  }

  override fun getBackground(): Color {
    return if (!UIUtil.isUnderDarcula())
      UISettings.instance.backgroundColor
    else
      UIUtil.getPanelBackground()
  }

  fun makeNextButtonSelected() {
    rootPane?.defaultButton = nextButton
    nextButton.isSelected = true
    nextButton.isFocusable = true
    nextButton.requestFocus()
  }

  fun clearRestoreMessage() {
    lessonMessagePane.clearRestoreMessages()
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

  companion object {
    private val LOG = Logger.getInstance(LearnPanel::class.java)
  }
}
