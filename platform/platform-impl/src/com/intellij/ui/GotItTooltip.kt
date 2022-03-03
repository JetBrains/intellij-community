// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.collectors.fus.ui.GotItUsageCollector
import com.intellij.internal.statistic.collectors.fus.ui.GotItUsageCollectorGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.ui.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.io.StringReader
import java.net.URL
import javax.swing.*
import javax.swing.event.AncestorEvent
import javax.swing.text.*
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

@Service
class GotItTooltipService {
  val isFirstRun = checkFirstRun()

  private fun checkFirstRun(): Boolean {
    val prevRunBuild = PropertiesComponent.getInstance().getValue("gotit.previous.run")
    val currentRunBuild = ApplicationInfo.getInstance().build.asString()
    if (prevRunBuild != currentRunBuild) {
      PropertiesComponent.getInstance().setValue("gotit.previous.run", currentRunBuild)
      return true
    }
    return false
  }

  companion object {
    fun getInstance(): GotItTooltipService = service()
  }
}

/**
 * id is a unique id for the tooltip that will be used to store the tooltip state in <code>PropertiesComponent</code>
 * id has the following format: place.where.used - lowercase words separated with dots.
 * GotIt tooltip usage statistics can be properly gathered if its id prefix is registered in plugin.xml (PlatformExtensions.xml)
 * with gotItTooltipAllowlist extension point. Prefix can cover a whole class of different gotit tooltips.
 * If prefix is shorter than the whole ID then all different tooltip usages will be reported in one category described by the prefix.
 */
class GotItTooltip(@NonNls val id: String,
                   @Nls val text: String,
                   private val parentDisposable: Disposable? = null) : ToolbarActionTracker<Balloon>() {

  @Nls
  private var header: String = ""

  @Nls
  private var buttonLabel: String = IdeBundle.message("got.it.button.name")

  private var shortcut: Shortcut? = null
  private var icon: Icon? = null
  private var timeout: Int = -1
  private var link: LinkLabel<Unit>? = null
  private var linkAction: () -> Unit = {}
  private var maxWidth = MAX_WIDTH
  private var showCloseShortcut = false
  private var maxCount = 1
  private var onBalloonCreated: (Balloon) -> Unit = {}

  private var useContrastColors = false

  // Ease the access (remove private or val to var) if fine-tuning is needed.
  private val savedCount: (String) -> Int = { PropertiesComponent.getInstance().getInt(it, 0) }
  var showCondition: (String) -> Boolean = { savedCount(it) in 0 until maxCount }

  private val gotIt: (String) -> Unit = {
    val count = savedCount(it)
    if (count in 0 until maxCount) PropertiesComponent.getInstance().setValue(it, (count + 1).toString())
    onGotIt()
  }
  private var onGotIt: () -> Unit = {}

  private val alarm = Alarm()
  private var balloon: Balloon? = null
  private var nextToShow: GotItTooltip? = null // Next tooltip in the global queue
  private var pendingRefresh = false
  var position: Balloon.Position = Balloon.Position.below

  init {
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this)
    }
  }

  override fun assignTo(presentation: Presentation, pointProvider: (Component, Balloon) -> Point) {
    this.pointProvider = pointProvider
    presentation.putClientProperty(PRESENTATION_GOT_IT_KEY as Key<Any>, this)
    Disposer.register(this, Disposable { presentation.putClientProperty(PRESENTATION_GOT_IT_KEY, null) })
  }

  /**
   * Add optional header to the tooltip.
   */
  fun withHeader(@Nls header: String): GotItTooltip {
    this.header = header
    return this
  }

  /**
   * Set preferred tooltip position relatively to the owner component
   */
  fun withPosition(position: Balloon.Position): GotItTooltip {
    this.position = position
    return this
  }

  /**
   * Add optional shortcut after mandatory description (text).
   */
  fun withShortcut(shortcut: Shortcut): GotItTooltip {
    this.shortcut = shortcut
    return this
  }

  /**
   * Set alternative button text instead of default "Got It".
   */
  fun withButtonLabel(@Nls label: String): GotItTooltip {
    this.buttonLabel = label
    return this
  }

  /**
   * Add optional icon on the left of the header or description.
   */
  fun withIcon(icon: Icon): GotItTooltip {
    this.icon = icon
    return this
  }

  /**
   * Set close timeout. If set then tooltip appears without "Got It" button.
   */
  @JvmOverloads
  fun withTimeout(timeout: Int = DEFAULT_TIMEOUT): GotItTooltip {
    if (timeout > 0) {
      this.timeout = timeout
    }
    return this
  }

  /**
   * Limit tooltip body width to the given value. By default it's limited to <code>MAX_WIDTH</code> pixels.
   */
  fun withMaxWidth(width: Int): GotItTooltip {
    maxWidth = width
    return this
  }

  /**
   * Add optional link to the tooltip.
   */
  fun withLink(@Nls linkLabel: String, action: () -> Unit): GotItTooltip {
    link = object : LinkLabel<Unit>(linkLabel, null) {
      override fun getNormal(): Color = JBUI.CurrentTheme.GotItTooltip.linkForeground()
    }
    linkAction = action
    return this
  }

  /**
   * Add optional link to the tooltip. Java version.
   */
  fun withLink(@Nls linkLabel: String, action: Runnable): GotItTooltip {
    return withLink(linkLabel) { action.run() }
  }

  /**
   * Add optional browser link to the tooltip. Link is rendered with arrow icon.
   */
  fun withBrowserLink(@Nls linkLabel: String, url: URL): GotItTooltip {
    link = object : LinkLabel<Unit>(linkLabel, AllIcons.Ide.External_link_arrow) {
      override fun getNormal(): Color = JBUI.CurrentTheme.GotItTooltip.linkForeground()
    }.apply { horizontalTextPosition = SwingConstants.LEFT }
    linkAction = { BrowserUtil.browse(url) }
    return this
  }

  /**
   * Set number of times the tooltip is shown.
   */
  fun withShowCount(count: Int): GotItTooltip {
    if (count > 0) maxCount = count
    return this
  }

  /**
   * Set whether to use contrast tooltip colors.
   */
  fun withContrastColors(contrastColors: Boolean): GotItTooltip {
    useContrastColors = contrastColors
    return this
  }

  /**
   * Optionally show close shortcut next to Got It button
   */
  fun andShowCloseShortcut(): GotItTooltip {
    showCloseShortcut = true
    return this
  }

  /**
   * Set notification method that's called when actual <code>Balloon</code> is created.
   */
  fun setOnBalloonCreated(callback: (Balloon) -> Unit): GotItTooltip {
    onBalloonCreated = callback
    return this
  }

  /**
   * Returns <code>true</code> if this tooltip can be shown at the given properties settings.
   */
  override fun canShow(): Boolean = showCondition("$PROPERTY_PREFIX.$id")

  /**
   * Show tooltip for the given component and point to the component.
   * If the component is showing (see <code>Component.isShowing</code>) and has not empty bounds then
   * the tooltip is shown right away.
   * If the component is showing but has empty bounds (technically not visible) then tooltip is shown asynchronously
   * when component gets resized to not empty bounds.
   * If the component is not showing then tooltip is shown asynchronously when component is added to the hierarchy and
   * gets not empty bounds.
   *
   * not for actionButton
   */
  override fun show(component: JComponent, pointProvider: (Component, Balloon) -> Point) {
    if (canShow()) {
      if (component.isShowing) {
        if (!component.bounds.isEmpty) {
          showImpl(component, pointProvider)
        }
        else {
          component.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
              if (!event.component.bounds.isEmpty) {
                showImpl(event.component as JComponent, pointProvider)
              }
            }
          }.also { Disposer.register(this, Disposable { component.removeComponentListener(it) }) })
        }
      }
      else {
        component.addAncestorListener(object : AncestorListenerAdapter() {
          override fun ancestorAdded(ancestorEvent: AncestorEvent) {
            if (!ancestorEvent.component.bounds.isEmpty) {
              showImpl(ancestorEvent.component, pointProvider)
            }
            else {
              ancestorEvent.component.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(componentEvent: ComponentEvent) {
                  if (!componentEvent.component.bounds.isEmpty) {
                    showImpl(componentEvent.component as JComponent, pointProvider)
                  }
                }
              }.also { Disposer.register(this@GotItTooltip, Disposable { component.removeComponentListener(it) }) })
            }
          }

          override fun ancestorRemoved(ancestorEvent: AncestorEvent) {
            balloon?.let {
              it.hide(true)
              GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.AncestorRemoved)
            }
            balloon = null
          }
        }.also { Disposer.register(this, Disposable { component.removeAncestorListener(it) }) })
      }
    }
  }

  private fun showImpl(component: JComponent, pointProvider: (Component, Balloon) -> Point) {
    if (canShow()) {
      val balloonProperty = UIUtil.getClientProperty(component, BALLOON_PROPERTY)
      if (balloonProperty == null) {
        balloon = createAndShow(component, pointProvider).also { UIUtil.putClientProperty(component, BALLOON_PROPERTY, it) }
      }
      else if (balloonProperty is BalloonImpl && balloonProperty.isVisible) {
        balloonProperty.revalidate()
      }
    }
    else {
      hidePopup()
    }
  }

  override fun wasCreated(): Boolean {
    return balloon != null
  }

  override fun init(component: JComponent, pointProvider: (Component, Balloon) -> Point) {
    createAndShow(component, pointProvider)
  }

  fun createAndShow(component: JComponent, pointProvider: (Component, Balloon) -> Point): Balloon {
    val tracker = object : PositionTracker<Balloon>(component) {
      override fun recalculateLocation(balloon: Balloon): RelativePoint? =
        if (getComponent().isShowing)
          RelativePoint(component, pointProvider(component, balloon))
        else {
          balloon.hide(true)
          GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.AncestorRemoved)
          null
        }
    }
    val balloon = createBalloon().also {
      val dispatcherDisposable = Disposer.newDisposable()
      Disposer.register(this, dispatcherDisposable)

      it.addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          GotItUsageCollector.instance.logOpen(id, savedCount("$PROPERTY_PREFIX.$id") + 1)
        }

        override fun onClosed(event: LightweightWindowEvent) {
          HelpTooltip.setMasterPopupOpenCondition(tracker.component, null)
          UIUtil.putClientProperty(tracker.component as JComponent, BALLOON_PROPERTY, null)
          Disposer.dispose(dispatcherDisposable)

          if (event.isOk) {
            currentlyShown?.nextToShow = null
            currentlyShown = null

            gotIt()
          }
          else {
            pendingRefresh = true
          }
        }
      })

      IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.EventDispatcher { e ->
        if (e is KeyEvent && KeymapUtil.isEventForAction(e, CLOSE_ACTION_NAME)) {
          it.hide(true)
          GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.EscapeShortcutPressed)
          true
        }
        else false
      }, dispatcherDisposable)

      HelpTooltip.setMasterPopupOpenCondition(tracker.component) {
        it.isDisposed
      }

      onBalloonCreated(it)
    }
    this.balloon = balloon

    when {
      currentlyShown == null -> {
        balloon.show(tracker, position)
        currentlyShown = this
      }

      currentlyShown!!.pendingRefresh -> {
        nextToShow = currentlyShown!!.nextToShow
        balloon.show(tracker, position)
        currentlyShown = this
      }

      else -> {
        var tooltip = currentlyShown as GotItTooltip
        while (tooltip.nextToShow != null) {
          tooltip = tooltip.nextToShow as GotItTooltip
        }

        tooltip.scheduleNext(this) {
          if (tracker.component.isShowing && !tracker.component.bounds.isEmpty) {
            balloon.show(tracker, position)
            currentlyShown = this@GotItTooltip
          }
          else {
            nextToShow?.let { it.onGotIt() }
          }
        }
      }
    }

    return balloon
  }

  fun gotIt() = gotIt("$PROPERTY_PREFIX.$id")

  private fun scheduleNext(tooltip: GotItTooltip, show: () -> Unit) {
    nextToShow = tooltip
    onGotIt = show
  }

  private fun createBalloon(): Balloon {
    var button: JButton? = null
    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(createContent { button = it })
      .setDisposable(this)
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
      .createBalloon().also { it.setAnimationEnabled(false) }

    val collector = GotItUsageCollector.instance

    link?.apply {
      setListener(LinkListener { _, _ ->
        linkAction()
        balloon.hide(true)
        collector.logClose(id, GotItUsageCollectorGroup.CloseType.LinkClick)
      }, null)
    }

    button?.apply {
      addActionListener(ActionListener {
        balloon.hide(true)
        collector.logClose(id, GotItUsageCollectorGroup.CloseType.ButtonClick)
      })
    }

    if (timeout > 0) {
      alarm.cancelAllRequests()
      alarm.addRequest({
                         balloon.hide(true)
                         collector.logClose(id, GotItUsageCollectorGroup.CloseType.Timeout)
                       }, timeout)
    }

    return balloon
  }

  private fun createContent(buttonSupplier: (JButton) -> Unit): JComponent {
    val panel = JPanel(GridBagLayout())
    val gc = GridBag()
    val left = if (icon != null) 8 else 0
    val column = if (icon != null) 1 else 0

    icon?.let { panel.add(JLabel(it), gc.nextLine().next().anchor(GridBagConstraints.BASELINE)) }

    if (header.isNotEmpty()) {
      if (icon == null) gc.nextLine()

      val finalText = HtmlChunk.raw(header)
        .bold()
        .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(JBUI.CurrentTheme.GotItTooltip.foreground(useContrastColors))))
        .wrapWith(HtmlChunk.html())
        .toString()
      panel.add(JBLabel(finalText), gc.setColumn(column).anchor(GridBagConstraints.LINE_START).insetLeft(left))
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

    if (icon == null || header.isNotEmpty()) gc.nextLine()
    panel.add(LimitedWidthLabel(builder, maxWidth),
              gc.setColumn(column).anchor(GridBagConstraints.LINE_START).insets(if (header.isNotEmpty()) 5 else 0, left, 0, 0))

    link?.let {
      panel.add(it, gc.nextLine().setColumn(column).anchor(GridBagConstraints.LINE_START).insets(5, left, 0, 0))
    }

    if (timeout <= 0) {
      val button = JButton(buttonLabel).apply {
        isFocusable = false
        isOpaque = false
        putClientProperty("gotItButton", true)
        if (useContrastColors) {
          border = JBUI.Borders.empty(0, 0, 5, 0)
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

        @Suppress("HardCodedStringLiteral")
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

  override fun dispose() {
    hidePopup()
    removeMeFromQueue()
  }

  private fun removeMeFromQueue() {
    if (currentlyShown === this) currentlyShown = nextToShow
    else {
      var tooltip = currentlyShown
      while (tooltip != null) {
        if (tooltip.nextToShow === this) {
          tooltip.nextToShow = nextToShow
          break
        }
        tooltip = tooltip.nextToShow
      }
    }
  }

  override fun hidePopup() {
    balloon?.hide(false)
    balloon = null
  }

  override fun hideOrRepaint(component: JComponent) {
    balloon?.let {
      if (component.bounds.isEmpty) {
        hidePopup()
      }
      else if (it is BalloonImpl && it.isVisible) {
        it.revalidate()
      }
    }
  }

  companion object {

    @JvmField
    val ARROW_SHIFT = JBUIScale.scale(20) + Registry.intValue("ide.balloon.shadow.size") + BalloonImpl.ARC.get()
    const val PROPERTY_PREFIX = "got.it.tooltip"

    private val BALLOON_PROPERTY = Key<Balloon>("$PROPERTY_PREFIX.balloon")

    private const val DEFAULT_TIMEOUT = 5000 // milliseconds
    private const val CLOSE_ACTION_NAME = "CloseGotItTooltip"
    private val MAX_WIDTH = JBUIScale.scale(280)

    private val PANEL_MARGINS = JBUI.Borders.empty(7, 4, 9, 9)

    private val iconClasses = hashMapOf<String, Class<*>>()

    internal fun findIcon(src: String): Icon? {
      val iconClassName = src.split(".")[0]
      val iconClass = iconClasses[iconClassName] ?: AllIcons::class.java
      return IconLoader.findIcon(src, iconClass)
    }

    /**
     * Register icon class that's used for resolving icons from icon tags.
     * parentDisposable should be disposed on plugin removal.
     * AllIcons is used by default if corresponding icons class hasn't been registered or found so
     * there is not need to explicitly register it.
     */
    fun registerIconClass(iconClass: Class<*>, parentDisposable: Disposable) {
      iconClasses[iconClass.simpleName] = iconClass

      Disposer.register(parentDisposable) {
        iconClasses.remove(iconClass.simpleName)
      }
    }

    // Frequently used point providers
    @JvmField
    val TOP_MIDDLE: (Component, Any) -> Point = { it, _ -> Point(it.width / 2, 0) }

    @JvmField
    val LEFT_MIDDLE: (Component, Any) -> Point = { it, _ -> Point(0, it.height / 2) }

    @JvmField
    val RIGHT_MIDDLE: (Component, Any) -> Point = { it, _ -> Point(it.width, it.height / 2) }

    @JvmField
    val BOTTOM_MIDDLE: (Component, Any) -> Point = { it, _ -> Point(it.width / 2, it.height) }

    @JvmField
    val BOTTOM_LEFT: (Component, Any) -> Point = { it, _ -> Point(0, it.height) }

    // Global tooltip queue start element
    private var currentlyShown: GotItTooltip? = null
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
    val editorKit = GotItEditorKit()

    private fun createHTMLView(component: JComponent, html: String): View {
      val document = editorKit.createDocument(component.font, component.foreground)
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
          val icon = GotItTooltip.findIcon(src)
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