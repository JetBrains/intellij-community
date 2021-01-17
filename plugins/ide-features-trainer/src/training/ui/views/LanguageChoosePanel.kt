// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.BundlePlace
import training.learn.CourseManager
import training.learn.LearnBundle
import training.ui.LearnToolWindow
import training.ui.UISettings
import training.util.clearTrainingProgress
import training.util.resetPrimaryLanguage
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.BoxLayout
import javax.swing.border.EmptyBorder
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

sealed class LanguageChoosePanelPlace(bundleAppendix: String) : BundlePlace(bundleAppendix) {
  object WELCOME_SCREEN : LanguageChoosePanelPlace("")
  object TOOL_WINDOW : LanguageChoosePanelPlace(".tool.window")
}

class LanguageChoosePanel(private val toolWindow: LearnToolWindow?,
                          opaque: Boolean = true,
                          private val addButton: Boolean = true) : JPanel() {

  private val place: LanguageChoosePanelPlace = if (toolWindow == null) LanguageChoosePanelPlace.WELCOME_SCREEN else LanguageChoosePanelPlace.TOOL_WINDOW
  private val caption = JLabel()
  private val description = MyJTextPane(UISettings.instance.width)

  private val mainPanel = JPanel()

  private val myRadioButtonMap = mutableMapOf<JRadioButton, LanguageExtensionPoint<LangSupport>>()
  private val buttonGroup = ButtonGroup()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isFocusable = false

    init()
    isOpaque = opaque
    background = background
    initMainPanel()
    add(mainPanel)

    //set LearnPanel UI
    this.preferredSize = Dimension(UISettings.instance.width, 100)
    this.border = UISettings.instance.emptyBorder

    revalidate()
    repaint()
  }

  private fun init() {
    caption.isOpaque = false
    caption.font = UISettings.instance.modulesFont

    description.isOpaque = false
    description.isEditable = false
    description.alignmentX = Component.LEFT_ALIGNMENT
    description.margin = JBUI.emptyInsets()
    description.border = EmptyBorder(0, 0, 0, 0)

    StyleConstants.setFontFamily(REGULAR, UISettings.instance.plainFont.family)
    StyleConstants.setFontSize(REGULAR, UISettings.instance.fontSize.toInt())
    StyleConstants.setForeground(REGULAR, UISettings.instance.questionColor)

    StyleConstants.setFontFamily(REGULAR_GRAY, UISettings.instance.plainFont.family)
    StyleConstants.setFontSize(REGULAR_GRAY, UISettings.instance.fontSize.toInt())
    StyleConstants.setForeground(REGULAR_GRAY, UISettings.instance.descriptionColor)

    StyleConstants.setLeftIndent(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setRightIndent(PARAGRAPH_STYLE, 0f)
    StyleConstants.setSpaceAbove(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setSpaceBelow(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setLineSpacing(PARAGRAPH_STYLE, 0.0f)
  }

  private fun createLearnButton(): JButton {
    val button = JButton()
    button.isOpaque = false
    button.action = object : AbstractAction(LearnBundle.messageInPlace("learn.choose.language.button", place)) {
      override fun actionPerformed(e: ActionEvent?) {
        resetPrimaryLanguage(getActiveLangSupport())
        toolWindow?.setModulesPanel()
      }
    }
    return button
  }

  private fun createResetResultsButton(): JButton {
    val button = JButton()
    button.action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        button.isEnabled = false
      }
    }
    button.isOpaque = false
    button.action = object : AbstractAction(LearnBundle.message("learn.choose.language.button.reset.tool.window")) {
      override fun actionPerformed(e: ActionEvent?) = clearTrainingProgress()
    }
    return button
  }


  private fun initMainPanel() {
    mainPanel.apply {
      layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
      isOpaque = false
      isFocusable = false

      add(caption)
      add(Box.createVerticalStrut(JBUI.scale(12)))
      add(description)
      add(Box.createVerticalStrut(UISettings.instance.groupGap))
      if (place == LanguageChoosePanelPlace.TOOL_WINDOW) border = UISettings.instance.checkmarkShiftBorder
    }

    try {
      initSouthPanel()
    }
    catch (e: BadLocationException) {
      LOG.warn(e)
    }

    caption.text = LearnBundle.messageInPlace("learn.choose.language.caption", place)
    try {
      description.document.insertString(0, LearnBundle.messageInPlace("learn.choose.language.description", place), REGULAR)
    }
    catch (e: BadLocationException) {
      LOG.warn(e)
    }
  }

  @Throws(BadLocationException::class)
  private fun initSouthPanel() {
    val radioButtonPanel = JPanel()
    radioButtonPanel.isOpaque = false
    radioButtonPanel.border = EmptyBorder(0, 12, 0, 0)
    radioButtonPanel.layout = BoxLayout(radioButtonPanel, BoxLayout.PAGE_AXIS)

    val supportedLanguagesExtensions = LangManager.getInstance().supportedLanguagesExtensions
    if (supportedLanguagesExtensions.isEmpty()) {
      val message = LearnBundle.message("no.supported.languages.found")
      LOG.error(message)
      mainPanel.add(JLabel().apply {
        text = message
        font = UISettings.instance.boldFont
      })
      return
    }

    for (langSupportExt: LanguageExtensionPoint<LangSupport> in supportedLanguagesExtensions) {

      val radioButton = createRadioButton(langSupportExt) ?: continue
      buttonGroup.add(radioButton)
      //add radio buttons
      myRadioButtonMap[radioButton] = langSupportExt
      radioButtonPanel.add(radioButton, Component.LEFT_ALIGNMENT)
    }
    //set selected language if it is not started
    if (LangManager.getInstance().getLangSupport() != null) {
      val button = myRadioButtonMap.keys.firstOrNull { myRadioButtonMap[it]?.instance == LangManager.getInstance().getLangSupport() }
      if (button != null) buttonGroup.setSelected(button.model, true)
      else buttonGroup.setSelected(buttonGroup.elements.nextElement().model, true)
    }
    else {
      buttonGroup.setSelected(buttonGroup.elements.nextElement().model, true)
    }
    mainPanel.add(radioButtonPanel)
    mainPanel.add(Box.createVerticalStrut(UISettings.instance.groupGap))
    if (addButton) mainPanel.add(createLearnButton())
    if (addButton && place == LanguageChoosePanelPlace.TOOL_WINDOW) {
      mainPanel.add(Box.createVerticalStrut(UISettings.instance.languagePanelButtonsGap))
      mainPanel.add(createResetResultsButton())
    }
  }

  private fun createRadioButton(langSupportExt: LanguageExtensionPoint<LangSupport>): JRadioButton? {
    val lessonsCount = CourseManager.instance.calcLessonsForLanguage(langSupportExt.instance)
    val lang: Language = Language.findLanguageByID(langSupportExt.language) ?: return null
    val passedLessons = CourseManager.instance.calcPassedLessonsForLanguage(langSupportExt.instance)
    val passedString = if (passedLessons > 0) LearnBundle.message("language.specific.course.passed", passedLessons) else ""
    val buttonName = LearnBundle.message("language.specific.course.description",
                                         lang.displayName,
                                         lessonsCount, lessonsCount,
                                         passedString)
    val radioButton = JRadioButton(buttonName)
    radioButton.border = UISettings.instance.radioButtonBorder
    radioButton.isOpaque = false
    return radioButton
  }

  private inner class MyJTextPane(private val widthOfText: Int) : JTextPane() {

    override fun getPreferredSize(): Dimension {
      return Dimension(widthOfText, super.getPreferredSize().height)
    }

    override fun getMaximumSize(): Dimension {
      return preferredSize
    }

  }

  override fun getPreferredSize(): Dimension {
    return Dimension(mainPanel.minimumSize.getWidth().toInt() + (UISettings.instance.westInset + UISettings.instance.eastInset),
                     mainPanel.minimumSize.getHeight().toInt() + (UISettings.instance.northInset + UISettings.instance.southInset))
  }

  override fun getBackground(): Color {
    return if (!UIUtil.isUnderDarcula())
      UISettings.instance.backgroundColor
    else
      UIUtil.getPanelBackground()
  }

  fun getActiveLangSupport(): LangSupport {
    val activeButton: AbstractButton = buttonGroup.elements.toList().find { button -> button.isSelected }
                                       ?: throw Exception("Unable to get active language")
    assert(activeButton is JRadioButton)
    assert(myRadioButtonMap.containsKey(activeButton))
    return myRadioButtonMap[activeButton]!!.instance
  }

  companion object {
    private val LOG = Logger.getInstance(LanguageChoosePanel::class.java)
    private val REGULAR = SimpleAttributeSet()
    private val REGULAR_GRAY = SimpleAttributeSet()
    private val PARAGRAPH_STYLE = SimpleAttributeSet()
  }

}