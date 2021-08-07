package com.intellij.internal.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import com.intellij.util.animation.*
import com.intellij.util.animation.components.BezierPainter
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Point2D
import java.lang.Math.PI
import java.text.NumberFormat
import java.util.function.Consumer
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

@Suppress("HardCodedStringLiteral")
class AnimationPanelTestAction : DumbAwareAction("Show Animation Panel") {

  private class DemoPanel(val disposable: Disposable, val bezier: () -> Easing) : BorderLayoutPanel() {

    val textArea: JBTextArea

    init {
      preferredSize = Dimension(600, 300)
      border = JBUI.Borders.emptyLeft(12)

      textArea = JBTextArea()

      createScrollPaneWithAnimatedActions(textArea)
    }

    fun load() {
      loadStage1()
    }

    private fun loadStage1() {
      val baseColor = UIUtil.getTextFieldForeground()
      val showDemoBtn = JButton("Click to start a demo", AllIcons.Process.Step_1).also {
        it.border = JBUI.Borders.customLine(baseColor, 1)
        it.isContentAreaFilled = false
        it.isOpaque = true
      }
      val showTestPageLnk = ActionLink("or load the testing page")
      addToCenter(OpaquePanel(VerticalLayout(15, SwingConstants.CENTER)).also {
        it.add(showDemoBtn, VerticalLayout.CENTER)
        it.add(showTestPageLnk, VerticalLayout.CENTER)
      })

      val icons = arrayOf(AllIcons.Process.Step_1, AllIcons.Process.Step_2, AllIcons.Process.Step_3,
                          AllIcons.Process.Step_4, AllIcons.Process.Step_5, AllIcons.Process.Step_6,
                          AllIcons.Process.Step_7, AllIcons.Process.Step_8)

      val iconAnimator = JBAnimator(disposable).apply {
        period = 60
        isCyclic = true
        type = JBAnimator.Type.EACH_FRAME

        ignorePowerSaveMode()
      }

      iconAnimator.animate(animation(icons, showDemoBtn::setIcon).apply {
        duration = iconAnimator.period * icons.size
      })

      val fadeOutElements = listOf(
        transparent(baseColor) {
          showDemoBtn.foreground = it
          showDemoBtn.border = JBUI.Borders.customLine(it, 1)
        },
        animation {
          val array = showDemoBtn.text.toCharArray()
          array.shuffle()
          showDemoBtn.text = String(array)
        },
        transparent(showTestPageLnk.foreground, showTestPageLnk::setForeground).apply {
          runWhenScheduled {
            showDemoBtn.icon = EmptyIcon.ICON_16
            showDemoBtn.isOpaque = false
            showDemoBtn.repaint()
            Disposer.dispose(iconAnimator)
          }
          runWhenExpired {
            removeAll()
            revalidate()
            repaint()
          }
        }
      )

      showDemoBtn.addActionListener {
        animate {
          fadeOutElements + animation().runWhenExpired {
            loadStage2()
          }
        }
      }

      showTestPageLnk.addActionListener {
        animate {
          fadeOutElements + animation().runWhenExpired {
            loadTestPage()
          }
        }
      }

      showDemoBtn.addMouseListener(object : MouseAdapter() {

        private val consumers = arrayOf(
          consumer(DoubleColorFunction(showDemoBtn.background, showDemoBtn.foreground), showDemoBtn::setBackground),
          consumer(DoubleColorFunction(showDemoBtn.foreground, showDemoBtn.background), showDemoBtn::setForeground),
        )

        val animator = JBAnimator()
        val statefulEasing = CubicBezierEasing(0.215, 0.61, 0.355, 1.0).stateful()

        override fun mouseEntered(e: MouseEvent) {
          animator.animate(Animation(*consumers).apply {
            duration = 500
            easing = statefulEasing.coerceIn(statefulEasing.value, 1.0)
            duration = (duration * (1.0 - statefulEasing.value)).roundToInt()
          })
        }

        override fun mouseExited(e: MouseEvent) {
          animator.animate(Animation(*consumers).apply {
            delay = 50
            duration = 450
            easing = statefulEasing.coerceIn(0.0, statefulEasing.value).reverse()
            duration = (duration * statefulEasing.value).roundToInt()
          })
        }
      })
    }

    private fun loadStage2() {
      val clicks = 2
      val buttonDemo = SpringButtonPanel("Here!", Rectangle(width / 2 - 50, 10, 100, 40), clicks) {
        this@DemoPanel.remove(it)
        loadStage3()
      }
      addToCenter(buttonDemo)

      val scroller = ComponentUtil.getScrollPane(textArea) ?: error("text area has no scroll pane")
      if (scroller.parent == null) {
        textArea.text = """
            Hello! 
            
            Click the button ${clicks + 1} times.
          """.trimIndent()
        scroller.preferredSize = Dimension(this@DemoPanel.width, 0)
        addToTop(scroller)
      }

      JBAnimator().animate(let {
        val to = Dimension(this@DemoPanel.width, 100)
        animation(scroller.preferredSize, to, scroller::setPreferredSize)
          .setEasing(bezier())
          .runWhenUpdated {
            revalidate()
            repaint()
          }
      })
    }

    private fun loadStage3() {
      val scroller = ComponentUtil.getScrollPane(textArea) ?: error("text area has no scroll pane")
      if (scroller.parent == null) {
        textArea.text = "Good start!"
        scroller.preferredSize = Dimension(this@DemoPanel.width, 0)
        addToTop(scroller)
      }

      val oopsText = "Oops... Everything is gone"
      val sorryText = """
        $oopsText
        
        To check page scroll options insert a text
        and press UP or DOWN key.
        
        These values are funny:
        0.34, 1.56, 0.64, 1
      """.trimIndent()

      animate {
        type = JBAnimator.Type.EACH_FRAME

        makeSequent(
          animation(scroller.preferredSize, size, scroller::setPreferredSize).apply {
            duration = 500
            delay = 500
            easing = bezier()

            runWhenUpdated {
              revalidate()
              repaint()
            }
          },
          animation(textArea.text, oopsText, textArea::setText).apply {
            duration = ((textArea.text.length + oopsText.length) * 20).coerceAtMost(5_000)
            delay = 200
          },
          animation(oopsText, sorryText, textArea::setText).apply {
            duration = ((oopsText.length + sorryText.length) * 20).coerceAtMost(7_500)
            delay = 1000
          },
          animation(size, Dimension(width, height - 30), scroller::setPreferredSize).apply {
            delay = 2_500
            easing = bezier()

            runWhenUpdated {
              revalidate()
              repaint()
            }

            runWhenExpired {
              val link = ActionLink("Got it! Now, open the test panel") { loadTestPage() }
              val foreground = link.foreground
              val transparent = ColorUtil.withAlpha(link.foreground, 0.0)
              link.foreground = transparent
              addToBottom(Wrapper(FlowLayout(), link))

              animate(transparent(foreground, link::setForeground).apply {
                easing = easing.reverse()
              })
            }
          }
        )
      }
    }

    private fun loadTestPage() = hideAllComponentsAndRun {
      val linear = FillPanel("Linear")
      val custom = FillPanel("Custom")
      val content = Wrapper(HorizontalLayout(40)).apply {
        border = JBUI.Borders.empty(0, 40)
        add(custom, HorizontalLayout.CENTER)
        add(linear, HorizontalLayout.CENTER)
      }
      addToCenter(content)

      val animations = listOf(
        animation(custom::value::set),
        animation(linear::value::set),
        animation().runWhenUpdated {
          content.repaint()
        }
      )
      val fillers = JBAnimator(disposable)

      addToLeft(AnimationSettings { options ->
        fillers.period = options.period
        fillers.isCyclic = options.cyclic
        fillers.type = options.type
        animations[0].easing = bezier()
        animations[1].easing = Easing.LINEAR
        animations.forEach { animation ->
          animation.duration = options.duration
          animation.delay = options.delay
          animation.easing = animation.easing.coerceIn(
            options.coerceMin / 100.0,
            options.coerceMax / 100.0
          )
          if (options.inverse) {
            animation.easing = animation.easing.invert()
          }
          if (options.reverse) {
            animation.easing = animation.easing.reverse()
          }
          if (options.mirror) {
            animation.easing = animation.easing.mirror()
          }
        }
        fillers.animate(animations)
      })

      revalidate()
    }

    private fun hideAllComponentsAndRun(afterFinish: () -> Unit) {
      val remover = mutableListOf<Animation>()
      val scroller = ComponentUtil.getScrollPane(textArea)
      components.forEach { comp ->
        if (scroller === comp) {
          remover += animation(comp.preferredSize, Dimension(comp.width, 0), comp::setPreferredSize)
            .setEasing(bezier())
            .runWhenUpdated {
              revalidate()
              repaint()
            }
          remover += animation(textArea.text, "", textArea::setText).setEasing { x ->
            1 - (1 - x).pow(4.0)
          }
          remover += transparent(textArea.foreground, textArea::setForeground)
        }
        else if (comp is Wrapper && comp.targetComponent is ActionLink) {
          val link = comp.targetComponent as ActionLink
          remover += transparent(link.foreground, link::setForeground)
        }
      }

      remover += animation().runWhenExpired {
        removeAll()
        afterFinish()
      }

      remover.forEach {
        it.duration = 800
      }

      animate { remover }
    }

    private fun createScrollPaneWithAnimatedActions(textArea: JBTextArea): JBScrollPane {
      val pane = JBScrollPane(textArea)

      val commonAnimator = JBAnimator(disposable)

      create("Scroll Down") {
        val from: Int = pane.verticalScrollBar.value
        val to: Int = from + pane.visibleRect.height
        commonAnimator.animate(
          animation(from, to, pane.verticalScrollBar::setValue)
            .setDuration(350)
            .setEasing(bezier())
        )
      }.registerCustomShortcutSet(CustomShortcutSet.fromString("DOWN"), pane)

      create("Scroll Up") {
        val from: Int = pane.verticalScrollBar.value
        val to: Int = from - pane.visibleRect.height
        commonAnimator.animate(
          animation(from, to, pane.verticalScrollBar::setValue)
            .setDuration(350)
            .setEasing(bezier())
        )
      }.registerCustomShortcutSet(CustomShortcutSet.fromString("UP"), pane)

      return pane
    }
  }

  class Options {
    var period: Int = 10
    var duration: Int = 1000
    var delay: Int = 0
    var cyclic: Boolean = false
    var reverse: Boolean = false
    var inverse: Boolean = false
    var mirror: Boolean = false
    var coerceMin: Int = 0
    var coerceMax: Int = 100
    var type: JBAnimator.Type = JBAnimator.Type.IN_TIME
  }

  private class AnimationSettings(val onStart: (Options) -> Unit) : BorderLayoutPanel() {

    private val options = Options()

    init {
      val panel = panel {
        row("Duration:") {
          spinner(options::duration, 0, 5000, 50)
        }
        row("Period:") {
          spinner(options::period, 5, 1000, 5)
        }
        row("Delay:") {
          spinner(options::delay, 0, 5000, 100)
        }
        row {
          checkBox("Cyclic", options::cyclic)
        }
        row {
          checkBox("Reverse", options::reverse)
        }
        row {
          checkBox("Inverse", options::inverse)
        }
        row {
          checkBox("Mirror", options::mirror)
        }
        row("Coerce (%)") {
          spinner(options::coerceMin, 0, 100, 5)
          spinner(options::coerceMax, 0, 100, 5)
        }
        row {
          comboBox(EnumComboBoxModel(JBAnimator.Type::class.java), options::type, listCellRenderer { value, _, _ ->
            text = value.toString().split("_").joinToString(" ") {
              it.toLowerCase().capitalize()
            }
          })
        }
      }
      addToCenter(panel)
      addToBottom(JButton("Start Animation").apply {
        addActionListener {
          panel.apply()
          onStart(options)
        }
      })
    }
  }

  private class FillPanel(val description: String) : JComponent() {

    var value: Double = 1.0

    init {
      background = JBColor(0xC2D6F6, 0x455563)
      preferredSize = Dimension(40, 0)
      border = JBUI.Borders.empty(40, 5, 40, 0)
    }

    override fun paintComponent(g: Graphics) {
      val g2d = g as Graphics2D
      val insets = insets
      val bounds = g2d.clipBounds.apply {
        height -= insets.top + insets.bottom
        y += insets.top
      }

      val fillHeight = (bounds.height * value).roundToInt()
      val fillY = bounds.y + bounds.height - fillHeight.coerceAtLeast(0)
      g2d.color = background
      g2d.fillRect(bounds.x, fillY, bounds.width, fillHeight.absoluteValue)

      g2d.color = UIUtil.getPanelBackground()
      g2d.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(3f), 0f)
      g2d.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y)
      g2d.drawLine(bounds.x, bounds.y + bounds.height, bounds.x + bounds.width, bounds.y + bounds.height)

      val textX = bounds.width
      val textY = bounds.y + bounds.height
      g2d.color = UIUtil.getPanelBackground()
      g2d.font = g2d.font.deriveFont(30f).deriveFont(Font.BOLD)
      g2d.rotate(-PI / 2, textX.toDouble(), textY.toDouble())
      g2d.drawString(description, textX, textY)
    }
  }

  private class BezierEasingPanel : BorderLayoutPanel() {

    private val painter = BezierPainter(0.215, 0.61, 0.355, 1.0).also(this::addToCenter).apply {
      border = JBUI.Borders.empty(25)
    }
    private val display = ExpandableTextField().also(this::addToTop).apply {
      isEditable = false
      text = getControlPoints(painter).joinToString(transform = format::format)
      border = JBUI.Borders.empty(1)
    }

    init {
      border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1)
      painter.addPropertyChangeListener { e ->
        if (e.propertyName.endsWith("ControlPoint")) {
          display.text = getControlPoints(painter).joinToString(transform = format::format)
        }
      }
      display.setExtensions(
        ExtendableTextComponent.Extension.create(AllIcons.General.Reset, "Reset") {
          setControlPoints(painter, listOf(0.215, 0.61, 0.355, 1.0))
        },
        ExtendableTextComponent.Extension.create(AllIcons.Actions.MenuPaste, "Paste from Clipboard") {
          try {
            ClipboardUtil.getTextInClipboard()?.let {
              setControlPoints(painter, parseControlPoints(it))
            }
          }
          catch (ignore: NumberFormatException) {
            animate {
              listOf(animation(UIUtil.getErrorForeground(), UIUtil.getTextFieldForeground(), display::setForeground).apply {
                duration = 800
                easing = Easing { x -> x * x * x }
              })
            }
          }
        })
    }

    fun getEasing() = painter.getEasing()

    private fun getControlPoints(bezierPainter: BezierPainter): List<Double> = listOf(
      bezierPainter.firstControlPoint.x, bezierPainter.firstControlPoint.y,
      bezierPainter.secondControlPoint.x, bezierPainter.secondControlPoint.y,
    )

    private fun setControlPoints(bezierPainter: BezierPainter, values: List<Double>) {
      bezierPainter.firstControlPoint = Point2D.Double(values[0], values[1])
      bezierPainter.secondControlPoint = Point2D.Double(values[2], values[3])
    }

    @Throws(java.lang.NumberFormatException::class)
    private fun parseControlPoints(value: String): List<Double> = value.split(",").also {
      if (it.size != 4) throw NumberFormatException("Cannot parse $value;")
    }.map { it.trim().toDouble() }

    companion object {
      private val format = NumberFormat.getNumberInstance()
    }
  }

  private class SpringButtonPanel(text: String, start: Rectangle, val until: Int, val onFinish: Consumer<SpringButtonPanel>) : Wrapper() {

    val button = JButton(text)
    val animator = JBAnimator().apply {
      period = 5
      type = JBAnimator.Type.IN_TIME
    }
    val easing = Easing { x -> 1 + 2.7 * (x - 1).pow(3.0) + 1.7 * (x - 1).pow(2.0) }
    var turns = 0

    init {
      button.bounds = start
      button.addActionListener {
        if (turns >= until) {
          flyAway()
        } else {
          jump()
          turns++
        }
      }

      layout = null
      add(button)
    }

    private fun flyAway() {
      animator.animate(
        animation(button.bounds, Rectangle(width / 2, 0, 0, 0), button::setBounds).apply {
          duration = 350
          easing = this@SpringButtonPanel.easing

          runWhenExpired {
            onFinish.accept(this@SpringButtonPanel)
          }
        }
      )
    }

    private fun jump() {
      animator.animate(
        animation(button.bounds, generateBounds(), button::setBounds).apply {
          duration = 350
          easing = this@SpringButtonPanel.easing
        }
      )
    }

    private fun generateBounds(): Rectangle {
      val size = bounds
      val x = (Math.random() * size.width / 2).toInt()
      val y = (Math.random() * size.height / 2).toInt()
      val width = (Math.random() * (size.width - x)).coerceAtLeast(100.0).toInt()
      val height = (Math.random() * (size.height - y)).coerceAtLeast(24.0).toInt()
      return Rectangle(x, y, width, height)
    }

  }

  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(e.project) {
      val bezier = BezierEasingPanel()
      val demo = DemoPanel(disposable, bezier::getEasing)

      init {
        title = "Animation Panel Test Action"
        bezier.border = CompoundBorder(JBUI.Borders.emptyRight(12), bezier.border)
        setResizable(true)
        init()

        window.addWindowListener(object : WindowAdapter() {
          override fun windowOpened(e: WindowEvent?) {
            demo.load()
            window.removeWindowListener(this)
          }
        })
      }

      override fun createCenterPanel() = OnePixelSplitter(false, .3f).also { ops ->
        ops.firstComponent = bezier
        ops.secondComponent = demo
      }

      override fun createActions() = emptyArray<Action>()
    }.show()
  }
}