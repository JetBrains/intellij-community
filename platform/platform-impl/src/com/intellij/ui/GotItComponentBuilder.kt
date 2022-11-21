// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.BalloonBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionListener
import java.io.StringReader
import javax.swing.*
import javax.swing.text.*
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet
import kotlin.math.min

@ApiStatus.Internal
class GotItComponentBuilder(@Nls private val text: String) {
  private var image: Icon? = null

  @Nls
  private var header: String = ""
  private var icon: Icon? = null
  private var stepNumber: Int? = null

  private var shortcut: Shortcut? = null

  private var link: LinkLabel<Unit>? = null
  private var linkAction: () -> Unit = {}

  private var showButton: Boolean = true
  @Nls
  private var buttonLabel: String = IdeBundle.message("got.it.button.name")
  private var buttonAction: () -> Unit = {}
  private var showCloseShortcut = false

  private var maxWidth = MAX_WIDTH
  private var useContrastColors = false

  /**
   * Add optional image above the header or description
   */
  fun withImage(image: Icon): GotItComponentBuilder {
    val arcRatio = 16.0 / min(image.iconWidth, image.iconHeight)
    val rounded = RoundedIcon(image, arcRatio, false)
    this.image = adjustIcon(rounded)
    return this
  }

  /**
   * Add an optional header to the tooltip.
   */
  fun withHeader(@Nls header: String): GotItComponentBuilder {
    this.header = header
    return this
  }

  /**
   * Add optional icon on the left of the header or description.
   * Is not compatible with step number.
   */
  fun withIcon(icon: Icon): GotItComponentBuilder {
    if (stepNumber != null) {
      throw IllegalStateException("Icon and step number can not be showed both at once. Choose one of them.")
    }
    this.icon = adjustIcon(icon)
    return this
  }

  /**
   * Add optional step number on the left of the header or description.
   * The step will be rendered with one zero predecessor if step number is lower than 10.
   * For example: 01, 02, 10, 12.
   * The step number should be in the range [1, 99].
   * Is not compatible with icon.
   */
  fun withStepNumber(step: Int): GotItComponentBuilder {
    if (icon != null) {
      throw IllegalStateException("Icon and step number can not be showed both at once. Choose one of them.")
    }
    if (step !in 1 until 100) {
      throw IllegalArgumentException("The step should be in the range [1, 99]. Provided step number: $step")
    }
    this.stepNumber = step
    return this
  }

  /**
   * Add optional shortcut after mandatory description (text).
   */
  fun withShortcut(shortcut: Shortcut): GotItComponentBuilder {
    this.shortcut = shortcut
    return this
  }

  /**
   * Add an optional link to the tooltip.
   */
  fun withLink(@Nls linkLabel: String, action: () -> Unit): GotItComponentBuilder {
    link = object : LinkLabel<Unit>(linkLabel, null) {
      override fun getNormal(): Color = JBUI.CurrentTheme.GotItTooltip.linkForeground()
    }
    linkAction = action
    return this
  }

  /**
   * Add an optional browser link to the tooltip. Link is rendered with arrow icon.
   */
  fun withBrowserLink(@Nls linkLabel: String, action: () -> Unit): GotItComponentBuilder {
    link = object : LinkLabel<Unit>(linkLabel, AllIcons.Ide.External_link_arrow) {
      override fun getNormal(): Color = JBUI.CurrentTheme.GotItTooltip.linkForeground()
    }.apply { horizontalTextPosition = SwingConstants.LEFT }
    linkAction = action
    return this
  }

  /**
   * Show "Got It" button
   */
  fun showButton(show: Boolean): GotItComponentBuilder {
    this.showButton = show
    return this
  }

  /**
   * Set alternative button text instead of default "Got It".
   */
  fun withButtonLabel(@Nls label: String): GotItComponentBuilder {
    this.buttonLabel = label
    return this
  }

  /**
   * Action to invoke when "Got It" button clicked.
   */
  fun onButtonClick(action: () -> Unit): GotItComponentBuilder {
    this.buttonAction = action
    return this
  }

  /**
   * Show close shortcut next to the "Got It" button.
   */
  fun showCloseShortcut(show: Boolean): GotItComponentBuilder {
    showCloseShortcut = show
    return this
  }

  /**
   * Limit tooltip body width to the given value. By default, it's limited to `MAX_WIDTH` pixels.
   */
  fun withMaxWidth(width: Int): GotItComponentBuilder {
    maxWidth = width
    return this
  }

  /**
   * Set whether to use contrast tooltip colors.
   */
  fun withContrastColors(contrastColors: Boolean): GotItComponentBuilder {
    useContrastColors = contrastColors
    return this
  }

  fun build(parentDisposable: Disposable, additionalSettings: BalloonBuilder.() -> BalloonBuilder = { this }): Balloon {
    var button: JButton? = null
    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(createContent { button = it })
      .setDisposable(parentDisposable)
      .setHideOnAction(false)
      .setHideOnClickOutside(false)
      .setHideOnFrameResize(false)
      .setHideOnKeyOutside(false)
      .setHideOnClickOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setBorderColor(JBUI.CurrentTheme.GotItTooltip.borderColor(useContrastColors))
      .setCornerToPointerDistance(ARROW_SHIFT)
      .setFillColor(JBUI.CurrentTheme.GotItTooltip.background(useContrastColors))
      .setPointerSize(JBUI.size(16, 8))
      .additionalSettings()
      .createBalloon().also { it.setAnimationEnabled(false) }

    link?.apply {
      setListener(LinkListener { _, _ ->
        linkAction()
        balloon.hide(true)
      }, null)
    }

    button?.apply {
      addActionListener(ActionListener {
        buttonAction()
        balloon.hide(true)
      })
    }

    return balloon
  }

  private fun createContent(buttonSupplier: (JButton) -> Unit): JComponent {
    val panel = JPanel(GridBagLayout())
    val gc = GridBag()
    val left = if (icon != null || stepNumber != null) 8 else 0
    val column = if (icon != null || stepNumber != null) 1 else 0

    image?.let { panel.add(JLabel(it), gc.nextLine().next().anchor(GridBagConstraints.LINE_START).coverLine().insetBottom(12)) }

    icon?.let { panel.add(JLabel(it), gc.nextLine().next().anchor(GridBagConstraints.BASELINE)) }
    stepNumber?.let { step ->
      val label = JLabel(step.toString().padStart(2, '0'))
      label.foreground = JBUI.CurrentTheme.GotItTooltip.stepForeground()
      label.font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(JBFont.label().size.toFloat())
      panel.add(label, gc.nextLine().next().anchor(GridBagConstraints.BASELINE))
    }

    if (header.isNotEmpty()) {
      if (icon == null && stepNumber == null) gc.nextLine()

      val finalText = HtmlChunk.raw(header)
        .bold()
        .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(JBUI.CurrentTheme.GotItTooltip.headerForeground())))
        .wrapWith(HtmlChunk.html())
        .toString()
      panel.add(JBLabel(finalText), gc.setColumn(column).anchor(GridBagConstraints.LINE_START).insets(1, left, 0, 0))
    }

    val builder = HtmlBuilder()
    builder.append(HtmlChunk.raw(text).wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(
      JBUI.CurrentTheme.GotItTooltip.foreground(useContrastColors)))))
    shortcut?.let {
      builder.append(HtmlChunk.nbsp())
        .append(HtmlChunk.nbsp())
        .append(HtmlChunk.text(KeymapUtil.getShortcutText(it))
                  .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(JBUI.CurrentTheme.GotItTooltip.shortcutForeground(useContrastColors)))))
    }

    if (icon == null && stepNumber == null || header.isNotEmpty()) gc.nextLine()
    panel.add(LimitedWidthLabel(builder, maxWidth),
              gc.setColumn(column).anchor(GridBagConstraints.LINE_START).insets(if (header.isNotEmpty()) 5 else 0, left, 0, 0))

    link?.let {
      panel.add(it, gc.nextLine().setColumn(column).anchor(GridBagConstraints.LINE_START).insets(5, left, 0, 0))
    }

    if (showButton) {
      val button = JButton(buttonLabel).apply {
        if (ExperimentalUI.isNewUI()) {
          font = JBFont.label().asBold()
        }
        isFocusable = false
        isOpaque = false
        foreground = JBUI.CurrentTheme.GotItTooltip.buttonForeground()
        putClientProperty("gotItButton", true)
        if (useContrastColors) {
          border = JBUI.Borders.emptyBottom(5)
          background = Color(0, true)
          putClientProperty("JButton.backgroundColor", JBUI.CurrentTheme.GotItTooltip.buttonBackgroundContrast())
          putClientProperty("ActionToolbar.smallVariant", true) // remove shadow in darcula

          foreground = JBUI.CurrentTheme.GotItTooltip.buttonForegroundContrast()
        }
      }
      buttonSupplier(button)

      if (showCloseShortcut) {
        val buttonPanel = JPanel().apply { isOpaque = false }
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(button)
        buttonPanel.add(Box.createHorizontalStrut(JBUIScale.scale(UIUtil.DEFAULT_HGAP)))

        val closeShortcut = JLabel(KeymapUtil.getShortcutText(CLOSE_ACTION_NAME)).apply {
          foreground = JBUI.CurrentTheme.GotItTooltip.shortcutForeground(useContrastColors)
        }
        buttonPanel.add(closeShortcut)

        panel.add(buttonPanel, gc.nextLine().setColumn(column).insets(11, left, 0, 0).anchor(GridBagConstraints.LINE_START))
      }
      else {
        panel.add(button, gc.nextLine().setColumn(column).insets(11, left, 0, 0).anchor(GridBagConstraints.LINE_START))
      }
    }

    panel.background = JBUI.CurrentTheme.GotItTooltip.background(useContrastColors)
    panel.border = PANEL_MARGINS

    return panel
  }

  companion object {
    @JvmField
    val ARROW_SHIFT = JBUIScale.scale(20) + Registry.intValue("ide.balloon.shadow.size") + BalloonImpl.ARC.get()

    internal const val CLOSE_ACTION_NAME = "CloseGotItTooltip"

    private val MAX_WIDTH = JBUIScale.scale(280)
    private val PANEL_MARGINS = JBUI.Borders.empty(7, 4, 9, 9)

    internal fun findIcon(src: String): Icon? {
      return IconLoader.findIcon(src, GotItTooltip::class.java)?.let { icon ->
        adjustIcon(icon)
      }
    }

    // returns dark icon if GotIt tooltip background is dark
    private fun adjustIcon(icon: Icon): Icon {
      return if (ColorUtil.isDark(JBUI.CurrentTheme.GotItTooltip.background(false))) {
        IconLoader.getDarkIcon(icon, true)
      }
      else icon
    }
  }
}

private class LimitedWidthLabel(htmlBuilder: HtmlBuilder, limit: Int) : JLabel() {
  val htmlView: View

  init {
    var htmlText = htmlBuilder.wrapWith(HtmlChunk.div()).wrapWith(HtmlChunk.html()).toString()
    var view = createHTMLView(this, htmlText)
    var width = view.getPreferredSpan(View.X_AXIS)

    if (width > limit) {
      view = createHTMLView(this, htmlBuilder.wrapWith(HtmlChunk.div().attr("width", limit)).wrapWith(HtmlChunk.html()).toString())
      width = rows(view).maxOfOrNull { it.getPreferredSpan(View.X_AXIS) } ?: limit.toFloat()

      htmlText = htmlBuilder.wrapWith(HtmlChunk.div().attr("width", width.toInt())).wrapWith(HtmlChunk.html()).toString()
      view = createHTMLView(this, htmlText)
    }

    htmlView = view
    preferredSize = Dimension(view.getPreferredSpan(View.X_AXIS).toInt(), view.getPreferredSpan(View.Y_AXIS).toInt())
  }

  override fun paintComponent(g: Graphics) {
    val rect = Rectangle(width, height)
    JBInsets.removeFrom(rect, insets)
    htmlView.paint(g, rect)
  }

  private fun rows(root: View): Collection<View> {
    return ArrayList<View>().also { visit(root, it) }
  }

  private fun visit(view: View, collection: MutableCollection<View>) {
    val cname: String? = view.javaClass.canonicalName
    cname?.let { if (it.contains("ParagraphView.Row")) collection.add(view) }

    for (i in 0 until view.viewCount) {
      visit(view.getView(i), collection)
    }
  }

  companion object {
    private val editorKit = GotItEditorKit()

    private fun createHTMLView(component: JComponent, html: String): View {
      val document = editorKit.createDocument(component.font, component.foreground ?: UIUtil.getLabelForeground())
      StringReader(html).use { editorKit.read(it, document, 0) }

      val factory = editorKit.viewFactory
      return RootView(component, factory, factory.create(document.defaultRootElement))
    }
  }

  private class GotItEditorKit : HTMLEditorKit() {
    companion object {
      private const val STYLES = "p { margin-top: 0; margin-bottom: 0; margin-left: 0; margin-right: 0 }" +
                                 "body { margin-top: 0; margin-bottom: 0; margin-left: 0; margin-right: 0 }"
    }

    //TODO: refactor to com.intellij.util.ui.ExtendableHTMLViewFactory
    private val viewFactory = object : HTMLFactory() {
      override fun create(elem: Element): View {
        val attr = elem.attributes
        if ("icon" == elem.name) {
          val src = attr.getAttribute(HTML.Attribute.SRC) as String
          val icon = GotItComponentBuilder.findIcon(src)
          if (icon != null) {
            return GotItIconView(icon, elem)
          }
        }
        return super.create(elem)
      }
    }

    private val style = StyleSheet()
    private var initialized = false

    override fun getStyleSheet(): StyleSheet {
      if (!initialized) {
        StringReader(STYLES).use { style.loadRules(it, null) }
        style.addStyleSheet(super.getStyleSheet())
        initialized = true
      }
      return style
    }

    override fun getViewFactory(): ViewFactory = viewFactory

    fun createDocument(font: Font, foreground: Color): Document {
      val s = StyleSheet().also {
        it.addStyleSheet(styleSheet)
        it.addRule(displayPropertiesToCSS(font, foreground))
      }

      return HTMLDocument(s).also {
        it.asynchronousLoadPriority = Int.MAX_VALUE
        it.preservesUnknownTags = true
      }
    }

    private fun displayPropertiesToCSS(font: Font?, fg: Color?): String {
      val rule = StringBuilder("body {")
      font?.let {
        rule.append(" font-family: ").append(it.family).append(" ; ").append(" font-size: ").append(it.size).append("pt ;")
        if (it.isBold) rule.append(" font-weight: 700 ; ")
        if (it.isItalic) rule.append(" font-style: italic ; ")
      }

      fg?.let {
        rule.append(" color: #")

        if (it.red < 16) rule.append('0')
        rule.append(Integer.toHexString(it.red))

        if (it.green < 16) rule.append('0')
        rule.append(Integer.toHexString(it.green))

        if (it.blue < 16) rule.append('0')
        rule.append(Integer.toHexString(it.blue))

        rule.append(" ; ")
      }

      return rule.append(" }").toString()
    }
  }

  private class GotItIconView(private val icon: Icon, elem: Element) : View(elem) {
    private val hAlign = (elem.attributes.getAttribute(HTML.Attribute.HALIGN) as String?)?.toFloatOrNull() ?: 0.5f
    private val vAlign = (elem.attributes.getAttribute(HTML.Attribute.VALIGN) as String?)?.toFloatOrNull() ?: 0.75f

    override fun getPreferredSpan(axis: Int): Float =
      (if (axis == X_AXIS) icon.iconWidth else icon.iconHeight).toFloat()

    override fun getToolTipText(x: Float, y: Float, allocation: Shape): String? =
      if (icon is IconWithToolTip) icon.getToolTip(true)
      else
        element.attributes.getAttribute(HTML.Attribute.ALT) as String?

    override fun paint(g: Graphics, allocation: Shape) {
      allocation.bounds.let { icon.paintIcon(null, g, it.x, it.y) }
    }

    override fun modelToView(pos: Int, a: Shape, b: Position.Bias?): Shape {
      if (pos in startOffset..endOffset) {
        val rect = a.bounds
        if (pos == endOffset) {
          rect.x += rect.width
        }
        rect.width = 0
        return rect
      }
      throw BadLocationException("$pos not in range $startOffset,$endOffset", pos)
    }

    override fun getAlignment(axis: Int): Float =
      if (axis == X_AXIS) hAlign else vAlign

    override fun viewToModel(x: Float, y: Float, a: Shape, bias: Array<Position.Bias>): Int {
      val alloc = a as Rectangle
      if (x < alloc.x + alloc.width / 2f) {
        bias[0] = Position.Bias.Forward
        return startOffset
      }
      bias[0] = Position.Bias.Backward
      return endOffset
    }
  }

  private class RootView(private val host: JComponent, private val factory: ViewFactory, private val view: View) : View(null) {
    private var width: Float = 0.0f

    init {
      view.parent = this
      setSize(view.getPreferredSpan(X_AXIS), view.getPreferredSpan(Y_AXIS))
    }

    override fun preferenceChanged(child: View?, width: Boolean, height: Boolean) {
      host.revalidate()
      host.repaint()
    }

    override fun paint(g: Graphics, allocation: Shape) {
      val bounds = allocation.bounds
      view.setSize(bounds.width.toFloat(), bounds.height.toFloat())
      view.paint(g, bounds)
    }

    override fun setParent(parent: View) {
      throw Error("Can't set parent on root view")
    }

    override fun setSize(width: Float, height: Float) {
      this.width = width
      view.setSize(width, height)
    }

    // Mostly delegation
    override fun getAttributes(): AttributeSet? = null
    override fun getPreferredSpan(axis: Int): Float = if (axis == X_AXIS) width else view.getPreferredSpan(axis)
    override fun getMinimumSpan(axis: Int): Float = view.getMinimumSpan(axis)
    override fun getMaximumSpan(axis: Int): Float = Int.MAX_VALUE.toFloat()
    override fun getAlignment(axis: Int): Float = view.getAlignment(axis)
    override fun getViewCount(): Int = 1
    override fun getView(n: Int) = view
    override fun modelToView(pos: Int, a: Shape, b: Position.Bias): Shape = view.modelToView(pos, a, b)
    override fun modelToView(p0: Int, b0: Position.Bias, p1: Int, b1: Position.Bias, a: Shape): Shape = view.modelToView(p0, b0, p1, b1, a)
    override fun viewToModel(x: Float, y: Float, a: Shape, bias: Array<out Position.Bias>): Int = view.viewToModel(x, y, a, bias)
    override fun getDocument(): Document = view.document
    override fun getStartOffset(): Int = view.startOffset
    override fun getEndOffset(): Int = view.endOffset
    override fun getElement(): Element = view.element
    override fun getContainer(): Container = host
    override fun getViewFactory(): ViewFactory = factory
  }
}