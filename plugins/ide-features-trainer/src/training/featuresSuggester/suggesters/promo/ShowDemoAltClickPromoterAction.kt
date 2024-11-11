// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.featuresSuggester.suggesters.promo

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.hint.HintUtil.installInformationProperties
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.UiComponentsUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBPoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xdebugger.impl.ui.XDebuggerEmbeddedComboBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import training.featuresSuggester.FeatureSuggesterBundle
import java.awt.*
import javax.swing.*
import javax.swing.JLayeredPane.DEFAULT_LAYER
import javax.swing.JLayeredPane.PALETTE_LAYER

private val promoHeight get() = 100
private val promoWidth get() = 250
private val startCursorPosition = JBPoint(100, 100)

private val altShortcutText get() = "<shortcut raw=\"Alt\"/>"
private val altShortcutWidth get() = 50

private val altPlusClickShortcutText get() = "<shortcut raw=\"Alt + Click\"/>"
private val altPlusClickShortcutWidth get() = 100

private val evaluationPanelSize get() = JBDimension(45, 35)

private val shortcutOffset get() = 40

private class ShowDemoAltClickPromoterAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val evaluateUi = UiComponentsUtil.findUiComponent(project) { ui: XDebuggerEmbeddedComboBox<*> ->
      ui.isShowing && ui.name == "Debugger.EvaluateExpression.combobox"
    }

    if (evaluateUi != null) {
      val builder = GotItComponentBuilder { FeatureSuggesterBundle.message("alt.click.promo.text") }

      builder.withHeader(FeatureSuggesterBundle.message("alt.click.promo.header"))

      val disposable: Disposable = Disposer.newDisposable()
      builder.onButtonClick {
        Disposer.dispose(disposable)
      }

      val altClickPromoContent = AltClickPromoContent(project, disposable)
      val content = altClickPromoContent.createContentComponent()
      builder.withCustomComponentPromo(content)

      val showTooltipAt = Point(evaluateUi.width - JBUIScale.scale(promoWidth + 100), 0)

      val balloon = builder.build(AltClickServiceForAnimation.INSTANCE) {
        setShowCallout(true)
      }

      balloon.show(RelativePoint(evaluateUi, showTooltipAt), Balloon.Position.above)
      altClickPromoContent.initAndStartAnimation()
    }
    else {
      SampleDialogWrapper(project).showAndGet()
    }
  }
}

@Suppress("HardCodedStringLiteral")
private class SampleDialogWrapper(val project: Project) : DialogWrapper(true) {
  val content = AltClickPromoContent(project, disposable)
  init {
    @Suppress("DialogTitleCapitalization")
    title = "Alt+Click demo promoter"
    init()
  }

  override fun beforeShowCallback() {
    content.initAndStartAnimation()
  }

  override fun createCenterPanel(): JComponent? {
    return content.createContentComponent()
  }
}

private class AltClickPromoContent(val project: Project, val disposable: Disposable) {
  private lateinit var editor: EditorImpl
  private lateinit var panelWithAnimation: PanelWithAnimation
  private lateinit var dialogPane: JBLayeredPane

  fun initAndStartAnimation() {
    val fragment1 = "foo()"
    val (indexOfFoo, target1) = targetPointByOffset(fragment1, 2)
    val fragment2 = "foo().calc()"
    val (_, target2) = targetPointByOffset(fragment2, fragment1.length)
    val (_, target3) = targetPointByOffset(fragment2, 8)

    var length = 500

    val job = AltClickServiceForAnimation.INSTANCE.coroutineScope.launch(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement()) {
      while (true) {
        panelWithAnimation.cursorIcon = AllIcons.Windows.Mouse.CursorText
        animateMouseCursorMove(length, startCursorPosition, target1)

        var highlighter = addHighlightToFragment(indexOfFoo, fragment1)
        panelWithAnimation.cursorIcon = AllIcons.Windows.Mouse.CursorPointingHand
        dialogPane.repaint()
        delay(200)

        addShortcut(altShortcutText, altShortcutWidth)

        dialogPane.revalidate()
        dialogPane.repaint()

        animateMouseCursorMove(length, target1, target2)

        highlighter.dispose()
        highlighter = addHighlightToFragment(indexOfFoo, fragment2)

        dialogPane.repaint()
        animateMouseCursorMove(length, target2, target3)

        panelWithAnimation.removeAll()
        addShortcut(altPlusClickShortcutText, altPlusClickShortcutWidth)

        dialogPane.revalidate()
        dialogPane.repaint()

        animateMouseClick(target3)
        highlighter.dispose()

        panelWithAnimation.removeAll()

        addEvaluationResult(target3)
        addShortcut(altPlusClickShortcutText, altPlusClickShortcutWidth)

        dialogPane.revalidate()
        dialogPane.repaint()

        delay(1500)

        panelWithAnimation.removeAll()
      }
    }
    job.cancelOnDispose(disposable)
  }

  private fun addEvaluationResult(target3: Point) {
    val evaluationResultPanel = installInformationProperties(BorderLayoutPanel()).also { it ->
      it.add(HintUtil.createInformationComponent().also { c ->
        SimpleColoredText("42", SimpleTextAttributes.REGULAR_ATTRIBUTES).appendToComponent(c)
      })
      it.border = JBUI.Borders.empty(5)
    }

    val evalPanel = JPanel().also {
      it.layout = BoxLayout(it, BoxLayout.X_AXIS)
      it.border = PopupBorder.Factory.createPopupBorder(true).also { b -> b.setActive(true) }
      it.background = evaluationResultPanel.background
      it.add(evaluationResultPanel)
      it.preferredSize = evaluationPanelSize
      it.maximumSize = evaluationPanelSize
    }

    panelWithAnimation.add(Box.createRigidArea(Dimension(0, target3.y + JBUIScale.scale(10))))
    panelWithAnimation.add(NonOpaquePanel().also {
      it.layout = BoxLayout(it, BoxLayout.X_AXIS)
      it.alignmentX = Component.LEFT_ALIGNMENT
      it.add(Box.createRigidArea(Dimension(target3.x + JBUIScale.scale(15), 0)))
      it.add(evalPanel)
    })
  }

  private fun addShortcut(shortcutText: String, shortcutWidth: Int) {
    panelWithAnimation.add(Box.createVerticalGlue())
    panelWithAnimation.add(NonOpaquePanel().also { p ->
      p.alignmentX = Component.LEFT_ALIGNMENT
      p.layout = BoxLayout(p, BoxLayout.X_AXIS)
      p.add(Box.createRigidArea(JBDimension(shortcutOffset, 0)))
      val jBHtmlPane = JBHtmlPane(QuickDocHighlightingHelper.getDefaultDocStyleOptions(editor.colorsScheme, true), JBHtmlPaneConfiguration.builder().build()).also {
        it.background = editor.backgroundColor
        it.text = shortcutText
        it.font = it.font.deriveFont(JBUIScale.scale(10.0f))
        val textHeight = it.getFontMetrics(it.font).height + JBUIScale.scale(7) //max(JBUIScale.scale(5.0f), 5.0f)
        it.maximumSize = Dimension(JBUIScale.scale(shortcutWidth), textHeight.toInt())
      }
      p.add(jBHtmlPane)
    })
    panelWithAnimation.add(Box.createRigidArea(JBDimension(0, 10)))
  }

  private fun addHighlightToFragment(indexOfFoo: Int, fragment1: String): RangeHighlighter {
    val attributes = editor.colorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
    val highlighter = editor.markupModel.addRangeHighlighter(indexOfFoo, indexOfFoo + fragment1.length,
                                                             HighlighterLayer.SELECTION, attributes,
                                                             HighlighterTargetArea.EXACT_RANGE)
    return highlighter
  }

  private fun targetPointByOffset(fragment: String, shift: Int): Pair<Int, Point> {
    val indexOfFoo = editor.document.charsSequence.indexOf(fragment)
    val pointOfSymbol = editor.offsetToPoint2D(indexOfFoo + shift)
    val targetInEditor = Point(pointOfSymbol.x.toInt(), pointOfSymbol.y.toInt() + editor.lineHeight/2)
    val target = SwingUtilities.convertPoint(editor.contentComponent, targetInEditor, panelWithAnimation)
    return Pair(indexOfFoo, target)
  }

  private suspend fun animateMouseCursorMove(length: Int, start: Point, target: Point) {
    val startMillis = System.currentTimeMillis()
    for (i in 0 until length step 20) {
      val initialX = start.x
      val initialY = start.y
      val p = Point(initialX + (target.x - initialX) * i / length, initialY + (target.y - initialY) * i / length)
      panelWithAnimation.cursorPosition = p
      dialogPane.repaint()

      val d = (startMillis + i) - System.currentTimeMillis()
      if (d > 0) delay(d)
    }
  }

  private suspend fun animateMouseClick(clickPoint: Point) {
    val startMillis = System.currentTimeMillis()
    val steps = 255
    val duration = 300
    val interval = duration.toDouble() / steps

    for (i in 0 until steps) {
      val r = i*20/steps
      val alpha = 255 - i*255/steps
      panelWithAnimation.clickAnimationStatus = ClickAnimationStatus(clickPoint, r, JBColor(Color.ORANGE, Color.WHITE), alpha)
      dialogPane.repaint()

      val d = (startMillis + (i*interval).toLong()) - System.currentTimeMillis()
      if (d > 0) {
        delay(d)
      }
    }
    panelWithAnimation.clickAnimationStatus = null
  }


  fun createContentComponent(): JComponent {
    val editorFactory = EditorFactory.getInstance()
    val document = editorFactory.createDocument("""
      call(foo().calc())
    """.trimIndent())
    editor = editorFactory.createViewer(document) as EditorImpl
    editor.settings.let {
      it.isLineNumbersShown = false
      it.additionalLinesCount = 0
      it.isLineMarkerAreaShown = false
    }
    editor.fontSize = JBUIScale.scale(13)
    editor.setHorizontalScrollbarVisible(false)
    editor.setVerticalScrollbarVisible(false)

    dialogPane = JBLayeredPane()
    dialogPane.isFullOverlayLayout = true

    val animationLayer = PanelWithAnimation()
    panelWithAnimation = animationLayer
    animationLayer.layout = BoxLayout(animationLayer, BoxLayout.Y_AXIS)


    val behind = JPanel(BorderLayout())

    dialogPane.add(behind, DEFAULT_LAYER, 0)
    dialogPane.add(animationLayer, PALETTE_LAYER, 1)

    val fileType = Language.findLanguageByID("kotlin")?.associatedFileType
    if (fileType != null) {
      editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)
    }

    for (part in listOf(editor.contentComponent, editor.gutterComponentEx)) {
      for (listener in part.mouseListeners.toList()) {
        part.removeMouseListener(listener)
      }
      for (listener in part.mouseMotionListeners.toList()) {
        part.removeMouseMotionListener(listener)
      }

      part.isFocusable = false
    }

    behind.add(editor.component, BorderLayout.CENTER)
    editor.component.preferredSize = JBDimension(promoWidth, promoHeight)


    dialogPane.preferredSize = JBDimension(promoWidth, promoHeight)
    animationLayer.preferredSize = JBDimension(promoWidth, promoHeight)
    return dialogPane
  }
}

data class ClickAnimationStatus(val clickPoint: Point, val radius: Int, val color: Color, val alpha: Int)

private class PanelWithAnimation() : NonOpaquePanel() {
  var cursorPosition: Point? = null
  var cursorIcon = AllIcons.Ide.Rating

  var clickAnimationStatus: ClickAnimationStatus? = null

  override fun paint(g: Graphics) {
    super.paint(g)
    val config = GraphicsUtil.setupAAPainting(g)
    g as? Graphics2D ?: return
    cursorPosition?.let {
      cursorIcon.paintIcon(this, g, it.x, it.y)
    }
    clickAnimationStatus?.let {
      @Suppress("UseJBColor")
      g.color = Color(it.color.red, it.color.green, it.color.blue, it.alpha)
      g.fillOval(it.clickPoint.x - it.radius, it.clickPoint.y - it.radius, it.radius * 2, it.radius * 2)
    }
    config.restore()
  }
}

@Service
private class AltClickServiceForAnimation(val coroutineScope: CoroutineScope) : Disposable {
  override fun dispose() {
    // nothing to dispose by itself
  }

  companion object {
    @JvmStatic
    val INSTANCE: AltClickServiceForAnimation get() = service()
  }
}
