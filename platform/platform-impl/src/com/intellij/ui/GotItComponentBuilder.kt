// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.text.ShortcutsRenderingUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
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
import com.intellij.ui.GotItComponentBuilder.Companion.MAX_WIDTH
import com.intellij.ui.InlineCodeExtension.Companion.getStyles
import com.intellij.ui.InlineCodeExtension.Companion.patchCodeTags
import com.intellij.ui.ShortcutExtension.Companion.getStyles
import com.intellij.ui.ShortcutExtension.Companion.patchShortcutTags
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.util.SVGLoader
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.io.StringReader
import java.net.URL
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.event.HyperlinkEvent
import javax.swing.plaf.TextUI
import javax.swing.text.*
import javax.swing.text.View.X_AXIS
import javax.swing.text.View.Y_AXIS
import javax.swing.text.html.*
import javax.swing.text.html.ParagraphView
import kotlin.math.min

private sealed interface GotItPromoContent {
  val width: Int?
}

private data class GotItPromoImage(val image: Icon): GotItPromoContent {
  override val width: Int get() = image.iconWidth
}

private data class GotItPromoHtmlPage(val htmlText: String, val htmlPageSize: Dimension): GotItPromoContent {
  override val width: Int get() = htmlPageSize.width
}

private data class GotItPromoComponent(val component: Component): GotItPromoContent {
  override val width: Int? get() = component.preferredSize.width
}

@ApiStatus.Internal
class GotItComponentBuilder(textSupplier: GotItTextBuilder.() -> @Nls String) {
  constructor(text: @Nls String) : this({ text })

  @Nls
  private val text: String
  private val linkActionsMap: Map<Int, () -> Unit>
  private val iconsMap: Map<Int, Icon>

  private var withImageBorder: Boolean = false

  private var promoContent: GotItPromoContent? = null

  @Nls
  private var header: String = ""
  private var icon: Icon? = null
  @NlsSafe
  private var stepText: String? = null

  private var shortcut: Shortcut? = null

  private var link: LinkLabel<Unit>? = null
  private var linkAction: () -> Unit = {}
  private var afterLinkClickedAction: () -> Unit = {}

  private var showButton: Boolean = true
  @Nls
  private var buttonLabel: String = IdeBundle.message("got.it.button.name")
  private var buttonAction: () -> Unit = {}
  private var requestFocus: Boolean = false
  private var useContrastButton: Boolean = false

  @Nls
  private var secondaryButtonText: String? = null
  private var secondaryButtonAction: () -> Unit = {}

  private var escapeAction: (() -> Unit)? = null

  private var maxWidth = MAX_WIDTH
  private var useContrastColors = false

  init {
    val builder = GotItTextBuilderImpl()
    val rawText = textSupplier(builder)
    val withPatchedShortcuts = ShortcutExtension.patchShortcutTags(rawText, true)
    this.text = InlineCodeExtension.patchCodeTags(withPatchedShortcuts)
    this.linkActionsMap = builder.getLinkActions()
    this.iconsMap = builder.getIcons()
  }

  /**
   * Add optional custom component above the header or description
   */
  fun withCustomComponentPromo(component: Component): GotItComponentBuilder {
    if (promoContent != null) {
      error("Choose one of promo content")
    }
    promoContent = GotItPromoComponent(component)
    return this
  }

  /**
   * Add optional image above the header or description
   */
  fun withImage(image: Icon, withBorder: Boolean = true): GotItComponentBuilder {
    if (promoContent != null) {
      error("Choose one of promo content")
    }
    val arcRatio = JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.get().toDouble() / min(image.iconWidth, image.iconHeight)
    promoContent = GotItPromoImage(RoundedIcon(image, arcRatio, false))
    withImageBorder = withBorder
    return this
  }

  fun withBrowserPage(htmlText: String, size: Dimension, withBorder: Boolean = true): GotItComponentBuilder {
    if (promoContent != null) {
      error("Choose one of promo content")
    }
    promoContent = GotItPromoHtmlPage(htmlText, size)
    withImageBorder = withBorder
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
    if (stepText != null) {
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
    this.stepText = step.toString().padStart(2, '0')
    return this
  }

  /**
   * Add optional step number on the left of the header or description.
   * Is not compatible with icon.
   */
  fun withStepNumber(text: @NlsSafe String): GotItComponentBuilder {
    if (icon != null) {
      throw IllegalStateException("Icon and step number can not be showed both at once. Choose one of them.")
    }
    this.stepText = text
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
    link = createLinkLabel(linkLabel, JBUI.CurrentTheme.GotItTooltip.linkForeground(useContrastColors), isExternal = false)
    linkAction = action
    return this
  }

  /**
   * Add an optional browser link to the tooltip. Link is rendered with arrow icon.
   */
  fun withBrowserLink(@Nls linkLabel: String, url: URL): GotItComponentBuilder {
    link = createLinkLabel(linkLabel, JBUI.CurrentTheme.GotItTooltip.linkForeground(useContrastColors), isExternal = true)
    linkAction = { BrowserUtil.browse(url) }
    return this
  }

  private fun createLinkLabel(@Nls text: String, foreground: Color, isExternal: Boolean): LinkLabel<Unit> {
    return object : LinkLabel<Unit>(text,
                                    if (isExternal) AllIcons.Ide.External_link_arrow.colorizeIfPossible(foreground)
                                    else null) {
      override fun getNormal(): Color = foreground
      override fun getHover(): Color = foreground
      override fun getVisited(): Color = foreground
      override fun getActive(): Color = foreground
      override fun getUnderlineColor(): Color = JBUI.CurrentTheme.GotItTooltip.linkUnderline(useContrastColors, myUnderline, foreground)
      override fun getUnderlineShift(): Int = 4
    }.also {
      if (isExternal) it.horizontalTextPosition = SwingConstants.LEFT
    }
  }

  /**
   * Action to invoke when any link clicked (inline or separated)
   */
  fun onLinkClick(action: () -> Unit): GotItComponentBuilder {
    val curAction = afterLinkClickedAction
    afterLinkClickedAction = {
      curAction()
      action()
    }
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
   * Set whether to use contrast (Blue) color for GotIt button.
   * Note, that [withContrastColors] takes precedence over this setting.
   */
  fun withContrastButton(contrastButton: Boolean): GotItComponentBuilder {
    useContrastButton = contrastButton
    return this
  }

  /**
   * Action to invoke when "Got It" button clicked.
   */
  fun onButtonClick(action: () -> Unit): GotItComponentBuilder {
    val curAction = buttonAction
    buttonAction = {
      curAction()
      action()
    }
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
   * Show additional button on the right side of the "GotIt" button.
   * Will be shown only if "GotIt" button is shown.
   */
  fun withSecondaryButton(@Nls label: String, action: () -> Unit): GotItComponentBuilder {
    this.secondaryButtonText = label
    this.secondaryButtonAction = action
    return this
  }

  /**
   * Sets the action to be executed when the escape key is pressed.
   * Note that the popup will be closed after the action execution.
   * If escape action is not set, the popup won't be closed on the escape key.
   */
  fun onEscapePressed(action: () -> Unit): GotItComponentBuilder {
    this.escapeAction = action
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
    var secondaryButton: JButton? = null
    lateinit var description: JEditorPane
    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(createContent({ button = it }, { secondaryButton = it }, { description = it }))
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
        }
        else thisLogger().error("Unknown link: ${event.description}")
      }
    }

    link?.apply {
      setListener(LinkListener { _, _ ->
        linkAction()
        afterLinkClickedAction()
      }, null)
    }

    button?.apply {
      addActionListener {
        buttonAction()
        balloon.hide(true)
      }
    }

    secondaryButton?.apply {
      addActionListener {
        secondaryButtonAction()
        balloon.hide(true)
      }
    }

    if (escapeAction != null && balloon is BalloonImpl) {
      balloon.setHideListener {
        escapeAction?.invoke()
        balloon.hide(true)
      }
      balloon.setHideOnClickOutside(false)
    }

    return balloon
  }

  private fun createContent(buttonConsumer: (JButton) -> Unit,
                            secondaryButtonConsumer: (JButton) -> Unit,
                            descriptionConsumer: (JEditorPane) -> Unit): JComponent {
    val panel = JPanel(GridBagLayout())
    val gc = GridBag()
    val left = if (icon != null || stepText != null) JBUI.CurrentTheme.GotItTooltip.ICON_INSET.get() else 0
    val column = if (icon != null || stepText != null) 1 else 0

    val promo = promoContent
    if (promo != null) {
      val borderSize = 1  // do not scale
      val component = when(promo) {
        is GotItPromoHtmlPage -> {
          val browser = JBCefBrowser.createBuilder().setMouseWheelEventEnable(false).build()
          browser.loadHTML(promo.htmlText)
          val wrapper = object : Wrapper(browser.component) {
            /** JCEF component is painting the whole background rect with no regard to opaque property. It breaks rounded borders.
             *  So, it is a hack to paint the border again over the JCEF component to override it.
             */
            /** JCEF component is painting the whole background rect with no regard to opaque property. It breaks rounded borders.
             *  So, it is a hack to paint the border again over the JCEF component to override it.
             */
            override fun paint(g: Graphics?) {
              super.paint(g)
              super.paintBorder(g)
            }
          }
          wrapper.also {
            UIUtil.setNotOpaqueRecursively(it)
            val baseSize = promo.htmlPageSize
            val adjustedSize = Dimension(baseSize.width + 2 * borderSize, baseSize.height + 2 * borderSize)
            it.minimumSize = adjustedSize
            it.preferredSize = adjustedSize
          }
        }
        is GotItPromoImage -> JLabel(adjustIcon(promo.image, useContrastColors))
        is GotItPromoComponent -> JPanel().apply {
          add(promo.component)
        }
      }

      component.border = object : Border {
        override fun paintBorder(c: Component?, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
          val g2d = g.create() as Graphics2D
          try {
            val arc = JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.get().toDouble()
            val rect = Rectangle(0, 0, width, height)
            val roundedRect = RoundRectangle2D.Double(borderSize / 2.0, borderSize / 2.0,
                                                      width.toDouble() - borderSize, height.toDouble() - borderSize,
                                                      arc, arc)
            // Fill the corners with default background to override the background of JCEF component and create the rounded corners
            val path: Path2D = Path2D.Float(Path2D.WIND_EVEN_ODD)
            path.append(roundedRect, false)
            path.append(rect, false)

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.color = JBUI.CurrentTheme.GotItTooltip.background(useContrastColors)
            g2d.fill(path)
            // Then paint the border itself if it is specified
            if (withImageBorder) {
              g2d.color = JBUI.CurrentTheme.GotItTooltip.imageBorderColor(useContrastColors)
              RectanglePainter2D.DRAW.paint(g2d, 0.0, 0.0, width.toDouble(), height.toDouble(), arc,
                                            LinePainter2D.StrokeType.CENTERED, borderSize.toDouble(), RenderingHints.VALUE_ANTIALIAS_ON)
            }
          }
          finally {
            g2d.dispose()
          }
        }

        @Suppress("UseDPIAwareInsets")
        override fun getBorderInsets(c: Component?): Insets = borderSize.let { Insets(it, it, it, it) }

        override fun isBorderOpaque(): Boolean = true
      }

      panel.add(component,
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

    stepText?.let { step ->
      @Suppress("HardCodedStringLiteral")
      iconOrStepLabel = JLabel(step).apply {
        foreground = JBUI.CurrentTheme.GotItTooltip.stepForeground(useContrastColors)
        font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(JBFont.label().size.toFloat())
      }
      panel.add(iconOrStepLabel!!, gc.nextLine().next().anchor(GridBagConstraints.BASELINE))
    }

    if (header.isNotEmpty()) {
      if (icon == null && stepText == null) gc.nextLine()

      val finalText = HtmlChunk.raw(header)
        .bold()
        .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(JBUI.CurrentTheme.GotItTooltip.headerForeground(useContrastColors))))
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

    if (icon == null && stepText == null || header.isNotEmpty()) gc.nextLine()
    val textWidth = promoContent?.width?.let { width ->
      width - (iconOrStepLabel?.let { it.preferredSize.width + left } ?: 0)
    } ?: maxWidth
    val description = LimitedWidthEditorPane(builder,
                                             textWidth,
                                             useContrastColors,
                                             // allow to extend width only if there is no image and maxWidth was not changed by developer
                                             allowWidthExtending = promoContent == null && maxWidth == MAX_WIDTH,
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
        isFocusable = requestFocus
        if (requestFocus) {
          pressOnEnter()
        }
        isOpaque = false
        foreground = JBUI.CurrentTheme.GotItTooltip.buttonForeground()
        putClientProperty("gotItButton", true)
        if (useContrastColors) {
          putClientProperty("gotItButton.contrast", true)
        }
        else if (useContrastButton) {
          putClientProperty("gotItButton.contrast.only.button", true)
        }
        if (useContrastColors || useContrastButton) {
          foreground = JBUI.CurrentTheme.GotItTooltip.buttonForegroundContrast()
        }
      }
      buttonConsumer(button)

      val secondaryButton = secondaryButtonText?.let { buttonText: @Nls String ->
        val link = ActionLink(buttonText)
        link.foreground = JBUI.CurrentTheme.GotItTooltip.secondaryActionForeground(useContrastColors)
        link.isFocusable = requestFocus
        if (requestFocus) {
          link.pressOnEnter()
        }
        secondaryButtonConsumer(link)
        link
      }

      val buttonComponent: JComponent = if (secondaryButton != null) {
        JPanel().apply {
          isOpaque = false
          layout = BoxLayout(this, BoxLayout.X_AXIS)
          add(button)
          add(Box.createHorizontalStrut(JBUIScale.scale(16)))
          add(secondaryButton)
        }
      }
      else button

      if (requestFocus) {
        // Needed to provide right component to focus in com.intellij.ui.BalloonImpl.getContentToFocus
        panel.isFocusCycleRoot = true
        panel.isFocusTraversalPolicyProvider = true
        panel.focusTraversalPolicy = object : SortingFocusTraversalPolicy(Comparator { _, _ -> 0 }) {
          override fun getDefaultComponent(aContainer: Container?): Component {
            return button
          }

          override fun getComponentAfter(aContainer: Container?, aComponent: Component?): Component {
            return if (aComponent == button && secondaryButton != null) secondaryButton else button
          }

          override fun getComponentBefore(aContainer: Container?, aComponent: Component?): Component {
            return getComponentAfter(aContainer, aComponent)
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

    RemoteTransferUIManager.forceDirectTransfer(panel)
    return panel
  }

  private fun JButton.pressOnEnter() {
    val inputMap = getInputMap(JComponent.WHEN_FOCUSED)
    val pressedKeystroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
    if (inputMap[pressedKeystroke] == null) {
      inputMap.put(pressedKeystroke, "pressed")
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released")
    }
  }

  companion object {
    internal const val CLOSE_ACTION_NAME: String = "CloseGotItTooltip"

    internal const val MAX_LINES_COUNT: Int = 5

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
      val fillColor = JBUI.CurrentTheme.GotItTooltip.iconFillColor(useContrastColors)
      val borderColor = JBUI.CurrentTheme.GotItTooltip.iconBorderColor(useContrastColors)
      return if (fillColor != null && borderColor != null) {
        icon.colorizeIfPossible(fillColor, borderColor)
      }
      else if (ColorUtil.isDark(JBUI.CurrentTheme.GotItTooltip.background(useContrastColors))) {
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

    val lineSpacing = htmlBuilder.toString().let {
      if (it.contains("""<span class="code">""")
          || it.contains("""<span class="shortcut">""")
          || it.contains("""<icon src=""")) 0.2f
      else 0.1f
    }
    editorKit = createEditorKit(useContrastColors, lineSpacing, iconsMap)

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
    val linkStyles = "a { color: #${ColorUtil.toHex(JBUI.CurrentTheme.GotItTooltip.linkForeground(useContrastColors))} }"
    val shortcutStyles = ShortcutExtension.getStyles(JBUI.CurrentTheme.GotItTooltip.shortcutForeground(useContrastColors),
                                                     JBUI.CurrentTheme.GotItTooltip.shortcutBackground(useContrastColors),
                                                     JBUI.CurrentTheme.GotItTooltip.shortcutBorder(useContrastColors))
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
      .build().apply {
        // We need it, because otherwise code block or shortcut "boxes" are cut on the first row
        border = JBEmptyBorder(1, 0, 0, 0)
      }
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
@ApiStatus.Internal
@Deprecated(message = "Use JBHtmlPane and <shortcut> element instead")
class ShortcutExtension : ExtendableHTMLViewFactory.Extension {
  override fun invoke(elem: Element, defaultView: View): View? {
    val tagAttributes = elem.attributes.getAttribute(HTML.Tag.SPAN) as? AttributeSet
    return if (tagAttributes?.getAttribute(HTML.Attribute.CLASS) == "shortcut") {
      return ShortcutView(elem)
    }
    else null
  }

  companion object {
    fun getStyles(foreground: Color, background: Color, border: Color): String {
      return ".shortcut { font-weight: bold;" +
             " color: ${ColorUtil.toHtmlColor(foreground)};" +
             " background-color: ${ColorUtil.toHtmlColor(background)};" +
             " border-color: #${ColorUtil.toHex(border, true)} }"
    }

    fun patchShortcutTags(htmlText: @Nls String, needLogIncorrectInput: Boolean): @Nls String {
      val shortcuts: Sequence<MatchResult> = SHORTCUT_REGEX.findAll(htmlText)
      if (!shortcuts.any()) return htmlText

      val builder = StringBuilder()
      var ind = 0
      for (shortcut in shortcuts) {
        builder.append(htmlText.substring(ind, shortcut.range.first))
        val text = getShortcutText(shortcut.groups["type"]!!.value, shortcut.groups["value"]!!.value, needLogIncorrectInput)
        val span = shortcutSpan(text)
        builder.append("${StringUtil.NON_BREAK_SPACE}$span${StringUtil.NON_BREAK_SPACE}")
        ind = shortcut.range.last + 1
      }
      builder.append(htmlText.substring(ind))

      @Suppress("HardCodedStringLiteral")
      return builder.toString()
    }

    private fun getShortcutText(type: String, value: String, needLogIncorrectInput: Boolean): String {
      return when (type) {
        "actionId" -> {
          val shortcut = ShortcutsRenderingUtil.getShortcutByActionId(value)
          if (shortcut != null) {
            ShortcutsRenderingUtil.getKeyboardShortcutData(shortcut).first
          }
          else {
            if (ActionManager.getInstance().getAction(value) == null) {
              if (needLogIncorrectInput) thisLogger().error("No action with $value id is registered")
              return value
            }
            ShortcutsRenderingUtil.getGotoActionData(value, needLogIncorrectInput).first
          }
        }
        "raw" -> {
          val keyStroke = KeyStroke.getKeyStroke(value)
          if (keyStroke != null) {
            ShortcutsRenderingUtil.getKeyStrokeData(keyStroke).first
          }
          else {
            if (needLogIncorrectInput) thisLogger().error("Invalid key stroke: $value")
            value
          }
        }
        else -> {
          if (needLogIncorrectInput) thisLogger().error("Unknown shortcut type: $type, use 'actionId' or 'raw'")
          value
        }
      }
    }

    private fun shortcutSpan(shortcutText: @NlsSafe String) = HtmlChunk.span().attr("class", "shortcut").addText(shortcutText).toString()

    private val SHORTCUT_REGEX: Regex = Regex("""<shortcut (?<type>actionId|raw)="(?<value>[\w. ]+)"/>""")
  }

  private class ShortcutView(elem: Element) : InlineView(elem) {
    private val horizontalIndent: Float
      get() = getFloatAttribute(CSS.Attribute.MARGIN_LEFT, DEFAULT_HORIZONTAL_INDENT)
    private val verticalIndent: Float
      get() = getFloatAttribute(CSS.Attribute.MARGIN_TOP, DEFAULT_VERTICAL_INDENT)
    private val borderColor: Color? get() = borderColorAttr
    private val arcSize: Float
      get() = JBUIScale.scale(DEFAULT_ARC)

    override fun paint(g: Graphics, a: Shape) {
      val rectangles = calculateRectangles(a)

      val borderColor = borderColor
      val backgroundColor = background
      val g2d = g.create() as Graphics2D
      try {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        for (rect in rectangles ?: emptyList()) {
          g2d.color = backgroundColor
          g2d.fill(rect)
          if (borderColor != null) {
            g2d.color = borderColor
            g2d.draw(rect)
          }
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
      private const val DEFAULT_HORIZONTAL_INDENT: Float = 3f
      private const val DEFAULT_VERTICAL_INDENT: Float = 0f
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
@Deprecated(message = "Use JBHtmlPane and <code> element instead")
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
    private val borderColor: Color? get() = borderColorAttr
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
      private const val DEFAULT_VERTICAL_INDENT: Float = -0.5f
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
@Deprecated(message = "Use JBHtmlPane and CSS line-height property instead")
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

private val InlineView.borderColorAttr: Color? get() =
  attributes.getAttribute(CSS.Attribute.BORDER_TOP_COLOR)?.toString()?.let { ColorUtil.fromHex(it) }

private fun Icon.colorizeIfPossible(fillColor: Color, borderColor: Color = fillColor): Icon =
  (this as? CachedImageIcon)?.createWithPatcher(colorPatcher = object : SVGLoader.SvgElementColorPatcherProvider, SvgAttributePatcher {
    private var lastColor = Int.MIN_VALUE
    private var lastDigest: LongArray? = null

    override fun digest(): LongArray {
      val color = fillColor.rgb / 2 + borderColor.rgb / 2
      if (color == lastColor) {
        lastDigest?.let {
          return it
        }
      }

      val digest = longArrayOf(color.toLong(), 440413911775177385)
      lastColor = color
      lastDigest = digest
      return digest
    }

    override fun patchColors(attributes: MutableMap<String, String>) {
      setAttribute(attributes, "fill", fillColor)
      setAttribute(attributes, "stroke", borderColor)
    }

    override fun attributeForPath(path: String) = this

    private fun setAttribute(attributes: MutableMap<String, String>, key: String, color: Color) {
      if (!attributes.containsKey(key) || attributes[key] == "none") return

      attributes[key] = "rgb(${color.red},${color.green},${color.blue})"

      val alpha = color.alpha
      if (alpha != 255) {
        attributes["$key-opacity"] = "${alpha / 255f}"
      }
    }
  }) ?: this
