// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.views

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessCurrentProject
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.interfaces.Module
import training.ui.UISettings
import training.util.DataLoader
import training.util.createBalloon
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.HyperlinkEvent
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class ModulesPanel : JPanel() {

  private val modulesPanel = LearningItems()

  private val module2linklabel = BidirectionalMap<Module, LinkLabel<Any>>()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isFocusable = false
    isOpaque = true
    background = UISettings.instance.backgroundColor

    //Obligatory block
    setupFontStyles()
    initModulesPanel()
    add(modulesPanel)
    add(Box.createVerticalGlue())

    //set LearnPanel UI
    preferredSize = Dimension(UISettings.instance.width, 100)
    border = UISettings.instance.emptyBorderWithNoEastHalfNorth

    revalidate()
    repaint()
  }

  private fun setupFontStyles() {
    StyleConstants.setFontFamily(REGULAR, UISettings.instance.fontFace)
    StyleConstants.setFontSize(REGULAR, UISettings.instance.fontSize.toInt())
    StyleConstants.setForeground(REGULAR, UISettings.instance.descriptionColor)

    StyleConstants.setLeftIndent(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setRightIndent(PARAGRAPH_STYLE, 0f)
    StyleConstants.setSpaceAbove(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setSpaceBelow(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setLineSpacing(PARAGRAPH_STYLE, 0.0f)
  }

  private fun initModulesPanel() {
    val modules = CourseManager.instance.modules
    if (DataLoader.liveMode) {
      CourseManager.instance.clearModules()
      module2linklabel.clear()
    }

    modulesPanel.let {
      it.modules = modules
      it.updateItems(CourseManager.instance.unfoldModuleOnInit)
    }
  }

  private fun createFeedbackPanel(description: String) {
    modulesPanel.add(Box.createVerticalStrut(UISettings.instance.descriptionGap))
    val descriptionPane = createDescriptionPane("")
    descriptionPane.border = UISettings.instance.checkmarkShiftBorder
    val htmlEditorKit = UIUtil.getHTMLEditorKit()
    htmlEditorKit.styleSheet.addRule(
      "p { font-face: \"${UISettings.instance.fontFace}\";color: #${ColorUtil.toHex(UISettings.instance.descriptionColor)};}")
    htmlEditorKit.styleSheet.addRule("a { color: #${ColorUtil.toHex(JBUI.CurrentTheme.Link.linkPressedColor())};}")
    descriptionPane.editorKit = htmlEditorKit
    descriptionPane.addHyperlinkListener { e -> if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) BrowserUtil.browse (e.url) }
    @Suppress("HardCodedStringLiteral")
    descriptionPane.text = "<html><p>$description"
    modulesPanel.add(descriptionPane)
  }

  private fun createModuleNameLinkLabel(module: Module): LinkLabel<*> {
    val moduleName = LinkLabel<Any>(module.name, null)
    moduleName.name = "moduleName"
    module2linklabel[module] = moduleName
    moduleName.setListener({ _, _ ->
                             val project = guessCurrentProject(modulesPanel)
                             var lesson = module.giveNotPassedLesson()
                             if (lesson == null) lesson = module.lessons[0]
                             val dumbService = DumbService.getInstance(project)
                             if (dumbService.isDumb && !lesson.properties.canStartInDumbMode) {
                               val balloon = createBalloon(LearnBundle.message("indexing.message"))
                               balloon.showInCenterOf(module2linklabel[module])
                               return@setListener
                             }
                             try {
                               CourseManager.instance.openLesson(project, lesson)
                             }
                             catch (e: Exception) {
                               LOG.warn(e)
                             }
                           }, null)
    moduleName.font = UISettings.instance.modulesFont
    moduleName.alignmentY = Component.BOTTOM_ALIGNMENT
    moduleName.alignmentX = Component.LEFT_ALIGNMENT
    return moduleName
  }

  private fun delegateToLinkLabel(descriptionPane: ModuleDescriptionPane, moduleName: LinkLabel<*>): MouseListener {
    return object : MouseListener {
      override fun mouseClicked(e: MouseEvent) {
        moduleName.doClick()
      }

      override fun mousePressed(e: MouseEvent) {
        moduleName.doClick()
      }

      override fun mouseReleased(e: MouseEvent) {

      }

      override fun mouseEntered(e: MouseEvent) {
        moduleName.entered(e)
        descriptionPane.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      }

      override fun mouseExited(e: MouseEvent) {
        moduleName.exited(e)
        descriptionPane.cursor = Cursor.getDefaultCursor()
      }
    }
  }

  fun updateMainPanel() {
    modulesPanel.removeAll()
    initModulesPanel()
  }

  override fun getPreferredSize(): Dimension {
    return Dimension(modulesPanel.minimumSize.getWidth().toInt() + (UISettings.instance.westInset + UISettings.instance.westInset),
                     modulesPanel.minimumSize.getHeight().toInt() + (UISettings.instance.northInset + UISettings.instance.southInset))
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    paintModuleCheckmarks(g)
  }

  private fun paintModuleCheckmarks(g: Graphics) {
    if (module2linklabel.isNotEmpty()) {
      for ((module, linkLabel) in module2linklabel.entries) {
        if (module.giveNotPassedLesson() == null) {
          val point = linkLabel.locationOnScreen
          val basePoint = this.locationOnScreen
          val y = point.y + 1 - basePoint.y
          if (!SystemInfo.isMac) {
            FeaturesTrainerIcons.Img.Checkmark.paintIcon(this, g, UISettings.instance.westInset, y + 4)
          }
          else {
            FeaturesTrainerIcons.Img.Checkmark.paintIcon(this, g, UISettings.instance.westInset, y + 2)
          }
        }
      }
    }
  }

  companion object {

    private val REGULAR = SimpleAttributeSet()
    private val PARAGRAPH_STYLE = SimpleAttributeSet()

    private val LOG = Logger.getInstance(ModulesPanel::class.java)

    fun createModuleHeader(module: Module, moduleName: JComponent, foregroundColor: Color): JPanel {
      val moduleHeader = JPanel().apply {
        name = "moduleHeader"
        isFocusable = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = EmptyBorder(0, 0, 0, 0)
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
      }

      val progressStr = module.calcProgress()
      val progressLabel: JBLabel = if (progressStr != null) {
        JBLabel(progressStr)
      }
      else {
        JBLabel()
      }
      progressLabel.border = EmptyBorder(0, 5, 0, 5)
      progressLabel.name = "progressLabel"
      progressLabel.font = UISettings.instance.italicFont
      progressLabel.foreground = foregroundColor
      progressLabel.alignmentY = Component.BOTTOM_ALIGNMENT
      moduleName.alignmentY = Component.BOTTOM_ALIGNMENT
      moduleName.alignmentX = Component.LEFT_ALIGNMENT
      moduleHeader.add(moduleName)
      moduleHeader.add(UISettings.rigidGap(UISettings::progressGap, isVertical = false))
      moduleHeader.add(progressLabel)
      return moduleHeader
    }

    fun createDescriptionPane(module: Module): ModuleDescriptionPane {
      return createDescriptionPane(module.description)
    }

    private fun createDescriptionPane(description: String?): ModuleDescriptionPane {
      val descriptionPane = ModuleDescriptionPane(UISettings.instance.width)
      descriptionPane.name = "descriptionPane"
      descriptionPane.isEditable = false
      descriptionPane.isOpaque = false
      descriptionPane.setParagraphAttributes(PARAGRAPH_STYLE, true)
      try {
        descriptionPane.document.insertString(0, description, REGULAR)
      }
      catch (e: BadLocationException) {
        LOG.warn(e)
      }

      descriptionPane.alignmentX = Component.LEFT_ALIGNMENT
      descriptionPane.margin = JBUI.emptyInsets()
      return descriptionPane
    }
  }


}

class ModuleDescriptionPane internal constructor(private val widthOfText: Int) : JTextPane() {

  override fun getPreferredSize(): Dimension {
    return Dimension(widthOfText, super.getPreferredSize().height)
  }

  override fun getMaximumSize(): Dimension {
    return preferredSize
  }
}

