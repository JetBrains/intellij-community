// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.text.ShortcutsRenderingUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.BalloonBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.GotItComponentBuilder.Companion.EXTENDED_MAX_WIDTH
import com.intellij.ui.GotItComponentBuilder.Companion.MAX_LINES_COUNT
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionListener
import java.awt.geom.RoundRectangle2D
import java.io.StringReader
import java.net.URL
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.HyperlinkEvent
import javax.swing.plaf.TextUI
import javax.swing.text.*
import javax.swing.text.View.X_AXIS
import javax.swing.text.View.Y_AXIS
import javax.swing.text.html.*
import javax.swing.text.html.ParagraphView
import kotlin.math.min

@ApiStatus.Internal
class GotItComponentBuilder(textSupplier: GotItTextBuilder.() -> @Nls String) {
  @Nls
  private val text: String
  private val linkActionsMap: Map<Int, () -> Unit>
  private val iconsMap: Map<Int, Icon>

  private var image: Icon? = null

  @Nls
  private var header: String = ""
  private var icon: Icon? = null
  private var stepNumber: Int? = null

  private var shortcut: Shortcut? = null

  private var link: LinkLabel<Unit>? = null
  private var linkAction: () -> Unit = {}
  private var afterLinkClickedAction: () -> Unit = {}

  private var showButton: Boolean = true
  @Nls
  private var buttonLabel: String = IdeBundle.message("got.it.button.name")
  private var buttonAction: () -> Unit = {}
  private var requestFocus: Boolean = false

  private var showCloseShortcut = false

  private var maxWidth = MAX_WIDTH
  private var useContrastColors = false

  init {
    val builder = GotItTextBuilderImpl()
    val rawText = textSupplier(builder)
    val withPatchedShortcuts = ShortcutExtension.patchShortcutTags(rawText)
    this.text = InlineCodeExtension.patchCodeTags(withPatchedShortcuts)
    this.linkActionsMap = builder.getLinkActions()
    this.iconsMap = builder.getIcons()
  }

  /**
   * Add optional image above the header or description
   */
  fun withImage(image: Icon): GotItComponentBuilder {
    val arcRatio = JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.get().toDouble() / min(image.iconWidth, image.iconHeight)
    this.image = RoundedIcon(image, arcRatio, false)
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
    this.icon = icon
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
    link = createLinkLabel(linkLabel, isExternal = false)
    linkAction = action
    return this
  }

  /**
   * Add an optional browser link to the tooltip. Link is rendered with arrow icon.
   */
  fun withBrowserLink(@Nls linkLabel: String, url: URL): GotItComponentBuilder {
    link = createLinkLabel(linkLabel, isExternal = true)
    linkAction = { BrowserUtil.browse(url) }
    return this
  }

  private fun createLinkLabel(@Nls text: String, isExternal: Boolean): LinkLabel<Unit> {
    return object : LinkLabel<Unit>(text, if (isExternal) AllIcons.Ide.External_link_arrow else null) {
      override fun getNormal(): Color = JBUI.CurrentTheme.GotItTooltip.linkForeground()
      override fun getHover(): Color = JBUI.CurrentTheme.GotItTooltip.linkForeground()
      override fun getVisited(): Color = JBUI.CurrentTheme.GotItTooltip.linkForeground()
      override fun getActive(): Color = JBUI.CurrentTheme.GotItTooltip.linkForeground()
    }.also {
      if (isExternal) it.horizontalTextPosition = SwingConstants.LEFT
    }
  }

  /**
   * Action to invoke when any link clicked (inline or separated)
   */
  fun onLinkClick(action: () -> Unit): GotItComponentBuilder {
    afterLinkClickedAction = action
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
   * Request focus to "Got It" button after tooltip will be shown.
   * Default is false.
   */
  fun requestFocus(request: Boolean): GotItComponentBuilder {
    this.requestFocus = request
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
   * Limit tooltip text width to the given value. By default, it's limited to [MAX_WIDTH] pixels.
   * Note, that this limitation will not be taken into account if there is an image [withImage].
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
    lateinit var description: JEditorPane
    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(createContent({ button = it }, { description = it }))
      .setDisposable(parentDisposable)
      .setHideOnAction(false)
      .setHideOnClickOutside(false)
      .setHideOnFrameResize(false)
      .setHideOnKeyOutside(false)
      .setHideOnClickOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setRequestFocus(requestFocus)
      .setBorderColor(JBUI.CurrentTheme.GotItTooltip.borderColor(useContrastColors))
      .setCornerToPointerDistance(getArrowShift())
      .setFillColor(JBUI.CurrentTheme.GotItTooltip.background(useContrastColors))
      .setPointerSize(JBUI.size(16, 8))
      .setCornerRadius(JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.get())
      .additionalSettings()
      .createBalloon().also { it.setAnimationEnabled(false) }

    description.addHyperlinkListener { event ->
      if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        val action = event.description?.toIntOrNull()?.let(linkActionsMap::get)
        if (action != null) {
          action()
          afterLinkClickedAction()
          balloon.hide(true)
        }
        else thisLogger().error("Unknown link: ${event.description}")
      }
    }

    link?.apply {
      setListener(LinkListener { _, _ ->
        linkAction()
        afterLinkClickedAction()
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

  private fun createContent(buttonConsumer: (JButton) -> Unit, descriptionConsumer: (JEditorPane) -> Unit): JComponent {
    val panel = JPanel(GridBagLayout())
    val gc = GridBag()
    val left = if (icon != null || stepNumber != null) JBUI.CurrentTheme.GotItTooltip.ICON_INSET.get() else 0
    val column = if (icon != null || stepNumber != null) 1 else 0

    image?.let {
      val adjusted = adjustIcon(it, useContrastColors)
      panel.add(JLabel(adjusted),
                gc.nextLine().next()
                  .anchor(GridBagConstraints.LINE_START)
                  .coverLine()
                  .insets(JBUI.CurrentTheme.GotItTooltip.IMAGE_TOP_INSET.get(), 0, JBUI.CurrentTheme.GotItTooltip.IMAGE_BOTTOM_INSET.get(), 0))
    }

    var iconOrStepLabel: JLabel? = null
    icon?.let {
      val adjusted = adjustIcon(it, useContrastColors)
      iconOrStepLabel = JLabel(adjusted)
      panel.add(iconOrStepLabel!!, gc.nextLine().next().anchor(GridBagConstraints.BASELINE))
    }

    stepNumber?.let { step ->
      iconOrStepLabel = JLabel(step.toString().padStart(2, '0')).apply {
        foreground = JBUI.CurrentTheme.GotItTooltip.stepForeground(useContrastColors)
        font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(JBFont.label().size.toFloat())
      }
      panel.add(iconOrStepLabel!!, gc.nextLine().next().anchor(GridBagConstraints.BASELINE))
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
    builder.append(HtmlChunk.raw(text))
    shortcut?.let {
      builder.append(HtmlChunk.nbsp())
        .append(HtmlChunk.nbsp())
        .append(HtmlChunk.text(KeymapUtil.getShortcutText(it))
                  .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(JBUI.CurrentTheme.GotItTooltip.shortcutForeground(useContrastColors)))))
    }

    if (icon == null && stepNumber == null || header.isNotEmpty()) gc.nextLine()
    val textWidth = image?.let { img ->
      img.iconWidth - (iconOrStepLabel?.let { it.preferredSize.width + left } ?: 0)
    } ?: maxWidth
    val description = LimitedWidthEditorPane(builder,
                                             textWidth,
                                             useContrastColors,
                                             // allow to extend width only if there is no image and maxWidth was not changed by developer
                                             allowWidthExtending = image == null && maxWidth == MAX_WIDTH,
                                             iconsMap)
    descriptionConsumer(description)
    panel.add(description,
              gc.setColumn(column).anchor(GridBagConstraints.LINE_START)
                .insets(if (header.isNotEmpty()) JBUI.CurrentTheme.GotItTooltip.TEXT_INSET.get() else 0, left, 0, 0))

    link?.let {
      panel.add(it, gc.nextLine().setColumn(column).anchor(GridBagConstraints.LINE_START)
        .insets(JBUI.CurrentTheme.GotItTooltip.TEXT_INSET.get(), left, 0, 0))
    }

    if (showButton) {
      val button = JButton(buttonLabel).apply {
        if (ExperimentalUI.isNewUI()) {
          font = JBFont.label().asBold()
        }
        isFocusable = requestFocus
        isOpaque = false
        foreground = JBUI.CurrentTheme.GotItTooltip.buttonForeground()
        putClientProperty("gotItButton", true)
        if (useContrastColors) {
          putClientProperty("gotItButton.contrast", true)
          foreground = JBUI.CurrentTheme.GotItTooltip.buttonForegroundContrast()
        }
      }
      buttonConsumer(button)

      val buttonComponent: JComponent = if (showCloseShortcut) {
        val buttonPanel = JPanel().apply { isOpaque = false }
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(button)
        buttonPanel.add(Box.createHorizontalStrut(JBUIScale.scale(UIUtil.DEFAULT_HGAP)))

        val closeShortcut = JLabel(KeymapUtil.getShortcutText(CLOSE_ACTION_NAME)).apply {
          foreground = JBUI.CurrentTheme.GotItTooltip.shortcutForeground(useContrastColors)
        }
        buttonPanel.add(closeShortcut)
        buttonPanel
      }
      else button

      if (requestFocus) {
        // Needed to provide right component to focus in com.intellij.ui.BalloonImpl.getContentToFocus
        panel.isFocusTraversalPolicyProvider = true
        panel.focusTraversalPolicy = object : SortingFocusTraversalPolicy(Comparator { _, _ -> 0 }) {
          override fun getDefaultComponent(aContainer: Container?): Component {
            return button
          }
        }
      }

      panel.add(buttonComponent,
                gc.nextLine().setColumn(column)
                  .insets(JBUI.CurrentTheme.GotItTooltip.BUTTON_TOP_INSET.get(), left,
                          JBUI.CurrentTheme.GotItTooltip.BUTTON_BOTTOM_INSET.get(), 0)
                  .anchor(GridBagConstraints.LINE_START))
    }

    panel.background = JBUI.CurrentTheme.GotItTooltip.background(useContrastColors)
    panel.border = EmptyBorder(JBUI.CurrentTheme.GotItTooltip.insets())

    return panel
  }

  companion object {
    internal const val CLOSE_ACTION_NAME = "CloseGotItTooltip"

    internal const val MAX_LINES_COUNT = 5

    /**
     * Max width of the text if lines count is less than [MAX_LINES_COUNT]
     */
    private val MAX_WIDTH: Int
      get() = JBUIScale.scale(280)

    /**
     * Max width of the text if lines count with [MAX_WIDTH] is equal or more than [MAX_LINES_COUNT]
     */
    internal val EXTENDED_MAX_WIDTH: Int
      get() = JBUIScale.scale(328)

    @JvmStatic
    fun getArrowShift(): Int {
      return JBUIScale.scale(15) + JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.get()
    }

    // returns dark icon if GotIt tooltip background is dark
    internal fun adjustIcon(icon: Icon, useContrastColors: Boolean): Icon {
      return if (ColorUtil.isDark(JBUI.CurrentTheme.GotItTooltip.background(useContrastColors))) {
        IconLoader.getDarkIcon(icon, true)
      }
      else icon
    }
  }
}

private class LimitedWidthEditorPane(htmlBuilder: HtmlBuilder,
                                     maxWidth: Int,
                                     useContrastColors: Boolean,
                                     allowWidthExtending: Boolean,
                                     iconsMap: Map<Int, Icon>) : JEditorPane() {
  init {
    foreground = JBUI.CurrentTheme.GotItTooltip.foreground(useContrastColors)
    background = JBUI.CurrentTheme.GotItTooltip.background(useContrastColors)

    val increasedLineSpacing = htmlBuilder.toString().let {
      it.contains("""<span class="code">""") || it.contains("""<span class="shortcut">""")
    }
    editorKit = createEditorKit(useContrastColors, if (increasedLineSpacing) 0.2f else 0.1f, iconsMap)

    putClientProperty("caretWidth", 0)

    var additionalWidthAdded = false
    setTextAndUpdateLayout(htmlBuilder)
    if (getRootView().getPreferredSpan(X_AXIS) > maxWidth) {
      setTextAndUpdateLayout(htmlBuilder, maxWidth)
      var rows = getRows()
      if (rows.size >= MAX_LINES_COUNT && allowWidthExtending) {
        setTextAndUpdateLayout(htmlBuilder, EXTENDED_MAX_WIDTH)
        rows = getRows()
      }
      val width = rows.maxOfOrNull { it.getPreferredSpan(X_AXIS) + ADDITIONAL_WIDTH } ?: maxWidth.toFloat()
      setTextAndUpdateLayout(htmlBuilder, width.toInt())
      additionalWidthAdded = true
    }

    val root = getRootView()
    val width = root.getPreferredSpan(X_AXIS).toInt() + if (additionalWidthAdded) 0 else ADDITIONAL_WIDTH
    preferredSize = Dimension(width, root.getPreferredSpan(Y_AXIS).toInt())
  }

  private fun createEditorKit(useContrastColors: Boolean, lineSpacing: Float, iconsMap: Map<Int, Icon>): HTMLEditorKit {
    val styleSheet = StyleSheet()
    val linkStyles = "a { color: #${ColorUtil.toHex(JBUI.CurrentTheme.GotItTooltip.linkForeground())} }"
    val shortcutStyles = ShortcutExtension.getStyles(JBUI.CurrentTheme.GotItTooltip.shortcutForeground(useContrastColors),
                                                     JBUI.CurrentTheme.GotItTooltip.shortcutBackground(useContrastColors))
    val codeFont = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(JBFont.label().size.toFloat())
    val codeStyles = InlineCodeExtension.getStyles(JBUI.CurrentTheme.GotItTooltip.codeForeground(useContrastColors),
                                                   JBUI.CurrentTheme.GotItTooltip.codeBackground(useContrastColors),
                                                   JBUI.CurrentTheme.GotItTooltip.codeBorderColor(useContrastColors),
                                                   codeFont)
    StringReader(linkStyles + shortcutStyles + codeStyles).use { styleSheet.loadRules(it, null) }

    val iconsExtension = ExtendableHTMLViewFactory.Extensions.icons { iconKey ->
      val explicitIcon = iconKey.toIntOrNull()?.let { iconsMap[it] }
      if (explicitIcon != null) {
        GotItComponentBuilder.adjustIcon(explicitIcon, useContrastColors)
      }
      else IconLoader.findIcon(iconKey, GotItTooltip::class.java, true, false)?.let { icon ->
        GotItComponentBuilder.adjustIcon(icon, useContrastColors)
      }
    }

    return HTMLEditorKitBuilder()
      .withStyleSheet(styleSheet)
      .withViewFactoryExtensions(iconsExtension,
                                 ShortcutExtension(),
                                 InlineCodeExtension(),
                                 LineSpacingExtension(lineSpacing))
      .build()
  }

  private fun setTextAndUpdateLayout(builder: HtmlBuilder, width: Int? = null) {
    val div = HtmlChunk.div().let { if (width != null) it.attr("width", width) else it }
    text = builder.wrapWith(div).wrapWith(HtmlChunk.html()).toString()
    val root = getRootView()
    // Update layout to calculate actual bounds
    root.setSize(root.getPreferredSpan(X_AXIS), root.getPreferredSpan(Y_AXIS))
  }

  private fun getRootView(): View {
    return (this.ui as TextUI).getRootView(this)
  }

  private fun getRows(): Collection<View> {
    return ArrayList<View>().also { visit(getRootView(), it) }
  }

  private fun visit(view: View, collection: MutableCollection<View>) {
    val cname: String? = view.javaClass.canonicalName
    cname?.let { if (it.contains("ParagraphView.Row")) collection.add(view) }

    for (i in 0 until view.viewCount) {
      visit(view.getView(i), collection)
    }
  }

  override fun updateUI() {
    super.updateUI()
    isFocusable = false
    isEditable = false
    border = null
  }

  companion object {
    // We need to make the resulting width little more than preferred,
    // because size is calculated by summing up floats and there can be rounding problems
    private const val ADDITIONAL_WIDTH = 1
  }
}

/**
 * Render each part of the shortcut in the separate rounded rectangle
 *
 * Syntax is `<span class="shortcut">SHORTCUT_TEXT_HERE</span>`.
 * For example: `<span class="shortcut">Ctrl   Shift   A</span>`.
 *
 * Also, you can use higher level syntax like `<shortcut actionId="SOME_ACTION_ID"/>` or `<shortcut raw="KEYSTROKE_TEXT"/>`
 * if you patch input HTML text using [patchShortcutTags].
 *
 * To install this extension you need to add styles returned from [getStyles] to your [StyleSheet].
 */
private class ShortcutExtension : ExtendableHTMLViewFactory.Extension {
  override fun invoke(elem: Element, defaultView: View): View? {
    val tagAttributes = elem.attributes.getAttribute(HTML.Tag.SPAN) as? AttributeSet
    return if (tagAttributes?.getAttribute(HTML.Attribute.CLASS) == "shortcut") {
      return ShortcutView(elem)
    }
    else null
  }

  companion object {
    fun getStyles(foreground: Color, background: Color): String {
      return ".shortcut { font-weight: bold;" +
             " color: ${ColorUtil.toHtmlColor(foreground)};" +
             " background-color: ${ColorUtil.toHtmlColor(background)} }"
    }

    fun patchShortcutTags(htmlText: @Nls String): @Nls String {
      val shortcuts: Sequence<MatchResult> = SHORTCUT_REGEX.findAll(htmlText)
      if (!shortcuts.any()) return htmlText

      val builder = StringBuilder()
      var ind = 0
      for (shortcut in shortcuts) {
        builder.append(htmlText.substring(ind, shortcut.range.first))
        val text = getShortcutText(shortcut.groups["type"]!!.value, shortcut.groups["value"]!!.value)
        val span = shortcutSpan(text)
        builder.append("${StringUtil.NON_BREAK_SPACE}$span${StringUtil.NON_BREAK_SPACE}")
        ind = shortcut.range.last + 1
      }
      builder.append(htmlText.substring(ind))

      @Suppress("HardCodedStringLiteral")
      return builder.toString()
    }

    private fun getShortcutText(type: String, value: String): String {
      return when (type) {
        "actionId" -> {
          val shortcut = ShortcutsRenderingUtil.getShortcutByActionId(value)
          if (shortcut != null) {
            ShortcutsRenderingUtil.getKeyboardShortcutData(shortcut).first
          }
          else ShortcutsRenderingUtil.getGotoActionData(value).first
        }
        "raw" -> {
          val keyStroke = KeyStroke.getKeyStroke(value)
          if (keyStroke != null) {
            ShortcutsRenderingUtil.getKeyStrokeData(keyStroke).first
          }
          else {
            thisLogger().error("Invalid key stroke: $value")
            value
          }
        }
        else -> {
          thisLogger().error("Unknown shortcut type: $type, use 'actionId' or 'raw'")
          value
        }
      }
    }

    private fun shortcutSpan(shortcutText: @NlsSafe String) = HtmlChunk.span().attr("class", "shortcut").addText(shortcutText).toString()

    private val SHORTCUT_REGEX: Regex = Regex("""<shortcut (?<type>actionId|raw)="(?<value>\w+)"/>""")
  }

  private class ShortcutView(elem: Element) : InlineView(elem) {
    private val horizontalIndent: Float
      get() = getFloatAttribute(CSS.Attribute.MARGIN_LEFT, DEFAULT_HORIZONTAL_INDENT)
    private val verticalIndent: Float
      get() = getFloatAttribute(CSS.Attribute.MARGIN_TOP, DEFAULT_VERTICAL_INDENT)
    private val arcSize: Float
      get() = JBUIScale.scale(DEFAULT_ARC)

    private var rectangles: List<RoundRectangle2D>? = null

    override fun paint(g: Graphics, a: Shape) {
      if (rectangles == null) {
        rectangles = calculateRectangles(a)
      }

      foreground
      val backgroundColor = background
      val g2d = g.create() as Graphics2D
      try {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = backgroundColor
        for (rect in rectangles ?: emptyList()) {
          g2d.fill(rect)
        }
      }
      finally {
        g2d.dispose()
      }

      // It is a hack to not paint background in the super.paint()
      // because it is already painted in the shape we need
      try {
        background = null
        super.paint(g, a)
      }
      finally {
        background = backgroundColor
      }
    }

    override fun preferenceChanged(child: View?, width: Boolean, height: Boolean) {
      super.preferenceChanged(child, width, height)
      rectangles = null
    }

    private fun calculateRectangles(allocation: Shape): List<RoundRectangle2D>? {
      val startOffset = element.startOffset
      return try {
        val text = element.document.getText(startOffset, element.endOffset - startOffset)
        val horIndent = horizontalIndent
        val vertIndent = verticalIndent
        val parts = text.split(SHORTCUT_PART_REGEX)
        val rectangles = mutableListOf<RoundRectangle2D>()
        var curInd = 0
        for (part in parts) {
          val textRect = modelToView(startOffset + curInd, Position.Bias.Forward,
                                     startOffset + curInd + part.length, Position.Bias.Backward, allocation).bounds2D
          rectangles.add(RoundRectangle2D.Double(textRect.x - horIndent, textRect.y - vertIndent,
                                                 textRect.width + 2 * horIndent, textRect.height + 2 * vertIndent,
                                                 arcSize.toDouble(), arcSize.toDouble()))
          curInd += part.length + ShortcutsRenderingUtil.SHORTCUT_PART_SEPARATOR.length
        }
        return rectangles
      }
      catch (ex: BadLocationException) {
        // ignore
        null
      }
    }

    companion object {
      private const val DEFAULT_HORIZONTAL_INDENT: Float = 2.5f
      private const val DEFAULT_VERTICAL_INDENT: Float = 0.5f
      private const val DEFAULT_ARC: Float = 8.0f

      private val SHORTCUT_PART_REGEX = Regex(ShortcutsRenderingUtil.SHORTCUT_PART_SEPARATOR)
    }
  }
}

/**
 * Render inline code element surrounding it by thin border.
 *
 * Syntax is `<span class="code">CODE_TEXT_HERE</span>`.
 *
 * Also, you can use higher level syntax like `<code>CODE_TEXT_HERE</code>` if you patch input HTML text using [patchCodeTags].
 *
 * To install this extension you need to add styles returned from [getStyles] to your [StyleSheet].
 */
private class InlineCodeExtension : ExtendableHTMLViewFactory.Extension {
  override fun invoke(elem: Element, defaultView: View): View? {
    val tagAttributes = elem.attributes.getAttribute(HTML.Tag.SPAN) as? AttributeSet
    return if (tagAttributes?.getAttribute(HTML.Attribute.CLASS) == "code") {
      return InlineCodeView(elem)
    }
    else null
  }

  companion object {
    fun getStyles(foreground: Color, background: Color, borderColor: Color, font: Font): String {
      val fontStyles = StringBuilder()
      fontStyles.append(" font-family: ").append(font.family).append(";").append(" font-size: ").append(font.size).append("pt;")
      if (font.isBold) fontStyles.append(" font-weight: 700;")
      if (font.isItalic) fontStyles.append(" font-style: italic;")
      return ".code { color: ${ColorUtil.toHtmlColor(foreground)};" +
             " background-color: ${ColorUtil.toHtmlColor(background)};" +
             " border-color: #${ColorUtil.toHex(borderColor, true)};" +
             fontStyles + " }"
    }

    fun patchCodeTags(htmlText: @Nls String): @Nls String {
      val builder = StringBuilder()
      var codeStartTagInd = htmlText.indexOf("<code>")
      val tagLength = 6
      var ind = 0
      while (codeStartTagInd != -1) {
        builder.append(htmlText.substring(ind, codeStartTagInd))
        val codeEndTagInd = htmlText.indexOf("</code>", codeStartTagInd)
        if (codeEndTagInd == -1) {
          error("<code> tag opened but do not closed, html text:\n$htmlText")
        }
        val text = htmlText.substring(codeStartTagInd + tagLength, codeEndTagInd)
        val span = codeSpan(text.replace(" ", StringUtil.NON_BREAK_SPACE))
        builder.append("${StringUtil.NON_BREAK_SPACE}$span${StringUtil.NON_BREAK_SPACE}")
        ind = codeEndTagInd + tagLength + 1
        codeStartTagInd = htmlText.indexOf("<code>", ind)
      }
      builder.append(htmlText.substring(ind))

      @Suppress("HardCodedStringLiteral")
      return builder.toString()
    }

    private fun codeSpan(text: @NlsSafe String) = HtmlChunk.span().attr("class", "code").addText(text).toString()
  }

  private class InlineCodeView(elem: Element) : InlineView(elem) {
    private val horizontalIndent: Float
      get() = getFloatAttribute(CSS.Attribute.MARGIN_LEFT, DEFAULT_HORIZONTAL_INDENT)
    private val verticalIndent: Float
      get() = getFloatAttribute(CSS.Attribute.MARGIN_TOP, DEFAULT_VERTICAL_INDENT)
    private val borderColor: Color?
      get() = attributes.getAttribute(CSS.Attribute.BORDER_TOP_COLOR)?.toString()?.let { ColorUtil.fromHex(it) }
    private val arcSize: Float
      get() = JBUIScale.scale(DEFAULT_ARC)

    override fun paint(g: Graphics, a: Shape) {
      val g2d = g.create() as Graphics2D
      val backgroundColor = background
      try {
        val baseRect = a.bounds2D
        val horIndent = horizontalIndent
        val vertIndent = verticalIndent
        val rect = RoundRectangle2D.Double(baseRect.x - horIndent, baseRect.y - vertIndent,
                                           baseRect.width + 2 * horIndent, baseRect.height + 2 * vertIndent,
                                           arcSize.toDouble(), arcSize.toDouble())
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = backgroundColor
        g2d.fill(rect)

        val borderBg = borderColor
        if (borderBg != null) {
          g2d.color = borderBg
          g2d.draw(rect)
        }
      }
      finally {
        g2d.dispose()
      }

      // It is a hack to not paint background in the super.paint()
      // because it is already painted in the shape we need
      try {
        background = null
        super.paint(g, a)
      }
      finally {
        background = backgroundColor
      }
    }

    companion object {
      private const val DEFAULT_HORIZONTAL_INDENT: Float = 2.5f
      private const val DEFAULT_VERTICAL_INDENT: Float = 0f
      private const val DEFAULT_ARC: Float = 8.0f
    }
  }
}

/**
 * It is impossible to edit the line spacing of the paragraphs in JEditorPane when HTMLEditorKit are used.
 * [CSS.Attribute.LINE_HEIGHT] is not taken into account, setting [AttributeSet] using [StyledDocument.setParagraphAttributes]
 * also do not propagate [StyleConstants.LineSpacing] to the [ParagraphView].
 * So, this is a workaround.
 */
private class LineSpacingExtension(val lineSpacing: Float) : ExtendableHTMLViewFactory.Extension {
  override fun invoke(elem: Element, defaultView: View): View? {
    return if (defaultView is ParagraphView) {
      object : ParagraphView(elem) {
        init {
          super.setLineSpacing(lineSpacing)
        }

        override fun setLineSpacing(ls: Float) {
          // do nothing to not override initial value
        }
      }
    }
    else null
  }
}

private fun View.getFloatAttribute(key: Any, defaultValue: Float): Float {
  val value = attributes.getAttribute(key)?.toString()?.toFloatOrNull() ?: defaultValue
  return JBUIScale.scale(value)
}