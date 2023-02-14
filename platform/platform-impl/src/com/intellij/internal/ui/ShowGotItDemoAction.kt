// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.Balloon.Position
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.GotItTextBuilder
import com.intellij.ui.GotItTooltip
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.random.Random
import kotlin.reflect.KMutableProperty0

class ShowGotItDemoAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val component = e.getData(CONTEXT_COMPONENT) as JComponent
    GotItConfigurationDialog(project, component).showAndGet()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && e.getData(CONTEXT_COMPONENT) is JComponent
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class GotItConfigurationDialog(private val project: Project, private val component: JComponent) : DialogWrapper(project, false) {
    private var text: String = """
      This is Debug tool window. 
      Here, you can use various actions like <shortcut actionId="GotoAction"/>,
      <b>Resume</b> <icon src="AllIcons.Actions.Resume"/>, 
      and <b>Stop</b> <icon src="AllIcons.Actions.Suspend"/>.""".trimIndent()
    private var addInlineLinks: Boolean = true

    private var showImage: Boolean = true
    private var imageWidth: Int = JBUI.scale(248)
    private var imageHeight: Int = JBUI.scale(132)

    private var showIconOrStep: Boolean = true
    private var showIcon: Boolean = true
    private var showStepNumber: Boolean = false
    private var stepNumber: Int = 1

    private var showHeader: Boolean = true
    private var headerText: String = "Some GotIt tooltip header"

    private var showLink: Boolean = false
    private var actionLink: Boolean = true
    private var actionLinkText: String = "Open Project tool window"
    private var browserLink: Boolean = false
    private var browserLinkText: String = "Open IDE web help"

    private var showButton: Boolean = true

    private val positionsModel: ComboBoxModel<Position> = DefaultComboBoxModel(Balloon.Position.values())
    private val position: Position
      get() = positionsModel.selectedItem as Position

    init {
      title = "GotIt Configuration"
      init()
    }

    override fun createCenterPanel(): JComponent = panel {
      row {
        textArea()
          .label("Text:")
          .columns(COLUMNS_LARGE)
          .bindText(::text)
          .align(AlignX.FILL)
      }
      row {
        checkBox("Add inline links to text").bindSelected(::addInlineLinks)
      }
      row {
        val checkbox = checkBox("Header:").bindSelected(::showHeader)
        textField()
          .bindText(::headerText)
          .enabledIf(checkbox.selected)
          .align(AlignX.FILL)
      }
      row {
        val checkbox = checkBox("Image").bindSelected(::showImage)
        intTextField(IntRange(JBUI.scale(100), JBUI.scale(500)))
          .label("Width:")
          .bindIntText(::imageWidth)
          .enabledIf(checkbox.selected)
        intTextField(IntRange(JBUI.scale(50), JBUI.scale(300)))
          .label("Height:")
          .bindIntText(::imageHeight)
          .enabledIf(checkbox.selected)
      }

      lateinit var iconOrStepCheckbox: Cell<JBCheckBox>
      row {
        @Suppress("DialogTitleCapitalization")
        iconOrStepCheckbox = checkBox("Icon or Step Number:").bindSelected(::showIconOrStep)
      }
      buttonsGroup(indent = true) {
        row {
          val button = radioButton("Step number:")
            .bindSelected(::showStepNumber)
          intTextField(IntRange(1, 99))
            .bindIntText(::stepNumber)
            .enabledIf(button.selected)
        }
        row {
          radioButton("Icon")
            .bindSelected(::showIcon)
        }
      }.enabledIf(iconOrStepCheckbox.selected)

      lateinit var linkCheckbox: Cell<JBCheckBox>
      row {
        linkCheckbox = checkBox("Link:").bindSelected(::showLink)
      }
      buttonsGroup(indent = true) {
        fun Row.radioButtonWithTextField(name: String, showProperty: KMutableProperty0<Boolean>, textProperty: KMutableProperty0<String>) {
          val button = radioButton(name)
            .bindSelected(showProperty)
          textField()
            .bindText(textProperty)
            .enabledIf(button.selected)
            .align(AlignX.FILL)
        }

        row {
          radioButtonWithTextField("Action:", ::actionLink,
                                   ::actionLinkText)
        }
        row {
          radioButtonWithTextField("Browser:", ::browserLink,
                                   ::browserLinkText)
        }
      }.enabledIf(linkCheckbox.selected)

      row {
        checkBox("GotIt button").bindSelected(::showButton)
      }
      row("Position:") {
        comboBox(positionsModel)
      }
    }

    override fun doOKAction() {
      super.doOKAction()

      val gotIt = buildGotItTooltip()
      val point = when (position) {
        Position.below -> GotItTooltip.BOTTOM_MIDDLE
        Position.above -> GotItTooltip.TOP_MIDDLE
        Position.atLeft -> GotItTooltip.LEFT_MIDDLE
        Position.atRight -> GotItTooltip.RIGHT_MIDDLE
      }
      gotIt.show(component, point)
    }

    private fun buildGotItTooltip(): GotItTooltip {
      val icon = AllIcons.General.BalloonInformation
      val image = createTestImage()

      val textSupplier: GotItTextBuilder.() -> String = {
        if (addInlineLinks) buildString {
          append(text)
          append(" ")
          append(link("Click") { ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.show() })
          append(" to open the Project tool window, or ")
          append(browserLink("open", URL("https://www.jetbrains.com/help/idea/getting-started.html")))
          append(" IDE help.")
        }
        else text
      }

      val randomId = Random(System.currentTimeMillis()).nextBytes(32).toString(StandardCharsets.UTF_8)
      val gotIt = GotItTooltip(randomId, textSupplier, Disposer.newDisposable())
      if (showImage) gotIt.withImage(image)
      if (showIconOrStep && showIcon) gotIt.withIcon(icon)
      if (showIconOrStep && showStepNumber) gotIt.withStepNumber(stepNumber)
      if (showHeader) gotIt.withHeader(headerText)
      if (showLink && actionLink) {
        gotIt.withLink(actionLinkText) { ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.show() }
      }
      if (showLink && browserLink) {
        gotIt.withBrowserLink(browserLinkText, URL("https://www.jetbrains.com/help/idea/getting-started.html"))
      }
      if (!showButton) gotIt.withTimeout(DateFormatUtil.HOUR.toInt())
      gotIt.withPosition(position)
      return gotIt
    }

    private fun createTestImage(): Icon {
      return object : Icon {
        override fun getIconWidth() = imageWidth

        override fun getIconHeight() = imageHeight

        @Suppress("UseJBColor")
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
          val g2d = g.create() as Graphics2D
          try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arcSize = JBUIScale.scale(8).toFloat()
            val rect = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), iconWidth.toFloat(), iconHeight.toFloat(), arcSize, arcSize)
            g2d.color = Color.lightGray
            g2d.draw(rect)
            LinePainter2D.paint(g2d, 0.0, 0.0, iconWidth.toDouble(), iconHeight.toDouble())
            LinePainter2D.paint(g2d, iconWidth.toDouble(), 0.0, 0.0, iconHeight.toDouble())
          }
          finally {
            g2d.dispose()
          }
        }
      }
    }
  }
}