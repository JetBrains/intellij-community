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
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.plaf.TextUI
import javax.swing.text.View
import javax.swing.text.View.X_AXIS
import javax.swing.text.View.Y_AXIS
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
      .setCornerRadius(JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.get())
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

    icon?.let {
      val adjusted = adjustIcon(it, useContrastColors)
      panel.add(JLabel(adjusted), gc.nextLine().next().anchor(GridBagConstraints.BASELINE))
    }

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
    panel.add(LimitedWidthEditorPane(builder, maxWidth, useContrastColors),
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
    @JvmField
    val ARROW_SHIFT = JBUIScale.scale(20) + Registry.intValue(
      "ide.balloon.shadow.size") + JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.get()

    internal const val CLOSE_ACTION_NAME = "CloseGotItTooltip"

    private val MAX_WIDTH = JBUIScale.scale(280)

    // returns dark icon if GotIt tooltip background is dark
    internal fun adjustIcon(icon: Icon, useContrastColors: Boolean): Icon {
      return if (ColorUtil.isDark(JBUI.CurrentTheme.GotItTooltip.background(useContrastColors))) {
        IconLoader.getDarkIcon(icon, true)
      }
      else icon
    }
  }
}

private class LimitedWidthEditorPane(htmlBuilder: HtmlBuilder, maxWidth: Int, useContrastColors: Boolean) : JEditorPane() {
  init {
    foreground = JBUI.CurrentTheme.GotItTooltip.foreground(useContrastColors)
    background = JBUI.CurrentTheme.GotItTooltip.background(useContrastColors)

    val iconsExtension = ExtendableHTMLViewFactory.Extensions.icons { iconKey ->
      IconLoader.findIcon(iconKey, GotItTooltip::class.java, true, false)?.let { icon ->
        GotItComponentBuilder.adjustIcon(icon, useContrastColors)
      }
    }
    editorKit = HTMLEditorKitBuilder()
      .withViewFactoryExtensions(iconsExtension)
      .build()

    setTextAndUpdateLayout(htmlBuilder)
    if (getRootView().getPreferredSpan(X_AXIS) > maxWidth) {
      setTextAndUpdateLayout(htmlBuilder, maxWidth)
      val width = rows().maxOfOrNull { it.getPreferredSpan(X_AXIS) } ?: maxWidth.toFloat()
      setTextAndUpdateLayout(htmlBuilder, width.toInt())
    }

    val root = getRootView()
    preferredSize = Dimension(root.getPreferredSpan(X_AXIS).toInt(), root.getPreferredSpan(Y_AXIS).toInt())
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

  private fun rows(): Collection<View> {
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
}