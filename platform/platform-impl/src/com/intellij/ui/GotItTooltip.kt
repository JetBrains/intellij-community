// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
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
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.*
import java.net.URL
import javax.swing.*
import javax.swing.event.AncestorEvent
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View

/**
 * id is a unique id for the tooltip that will be used to store the tooltip state in <code>PropertiesComponent</code>
 * id has the following format: place.where.used - lowercase words separated with dots.
 * GotIt tooltip usage statistics can be properly gathered if its id prefix is registered in plugin.xml (PlatformExtensions.xml)
 * with gotItTooltipAllowlist extension point. Prefix can cover a whole class of different gotit tooltips.
 * If prefix is shorter than the whole ID then all different tooltip usages will be reported in one category described by the prefix.
 */
class GotItTooltip(@NonNls val id: String, @Nls val text: String, parentDisposable: Disposable) : Disposable {
  private class ActionContext(val tooltip: GotItTooltip, val pointProvider: (Component) -> Point)

  @Nls
  private var header : String = ""

  @Nls
  private var buttonLabel: String = IdeBundle.message("got.it.button.name")

  private var shortcut: Shortcut? = null
  private var icon: Icon? = null
  private var timeout : Int = -1
  private var link : LinkLabel<Unit>? = null
  private var linkAction : () -> Unit = {}
  private var maxWidth = MAX_WIDTH
  private var showCloseShortcut = false
  private var maxCount = 1
  private var chainFunction: () -> Unit = {}
  private var position = Balloon.Position.below
  private var onBalloonCreated : (Balloon) -> Unit = {}

  // Ease the access (remove private or val to var) if fine tuning is needed.
  private val savedCount : (String) -> Int = { PropertiesComponent.getInstance().getInt(it, 0) }
  private val canShow : (String) -> Boolean = { savedCount(it) < maxCount }
  private val onGotIt : (String) -> Unit = {
    val count = savedCount(it)
    if (count in 0 until maxCount) PropertiesComponent.getInstance().setValue(it, (count + 1).toString())
  }

  private val alarm = Alarm()
  private var balloon : Balloon? = null

  init {
    Disposer.register(parentDisposable, this)
  }

  /**
   * Add optional header to the tooltip.
   */
  fun withHeader(@Nls header: String) : GotItTooltip {
    this.header = header
    return this
  }

  /**
   * Add optional shortcut after mandatory description (text).
   */
  fun withShortcut(shortcut: Shortcut) : GotItTooltip {
    this.shortcut = shortcut
    return this
  }

  /**
   * Set alternative button text instead of default "Got It".
   */
  fun withButtonLabel(@Nls label: String) : GotItTooltip {
    this.buttonLabel = label
    return this
  }

  /**
   * Add optional icon on the left of the header or description.
   */
  fun withIcon(icon: Icon) : GotItTooltip {
    this.icon = icon
    return this
  }

  /**
   * Set close timeout. If set then tooltip appears without "Got It" button.
   */
  @JvmOverloads
  fun withTimeout(timeout: Int = DEFAULT_TIMEOUT) : GotItTooltip {
    if (timeout > 0) {
      this.timeout = timeout
    }
    return this
  }

  /**
   * Limit tooltip body width to the given value. By default it's limited to <code>MAX_WIDTH</code> pixels.
   */
  fun withMaxWidth(width: Int) : GotItTooltip {
    maxWidth = width
    return this
  }

  /**
   * Add optional link to the tooltip.
   */
  fun withLink(@Nls linkLabel: String, action: () -> Unit) : GotItTooltip {
    link = LinkLabel<Unit>(linkLabel, null)
    linkAction = action
    return this
  }

  /**
   * Add optional link to the tooltip. Java version.
   */
  fun withLink(@Nls linkLabel: String, action: Runnable) : GotItTooltip {
    return withLink(linkLabel) { action.run() }
  }

  /**
   * Add optional browser link to the tooltip. Link is rendered with arrow icon.
   */
  fun withBrowserLink(@Nls linkLabel: String, url: URL) : GotItTooltip {
    link = LinkLabel<Unit>(linkLabel, AllIcons.Ide.External_link_arrow).apply{ horizontalTextPosition = SwingConstants.LEFT }
    linkAction = { BrowserUtil.browse(url) }
    return this
  }

  /**
   * Set number of times the tooltip is shown.
   */
  fun withShowCount(count: Int) : GotItTooltip {
    if (count > 0) maxCount = count
    return this
  }

  /**
   * Set preferred tooltip position relatively to the owner component
   */
  fun withPosition(position: Balloon.Position) : GotItTooltip {
    this.position = position
    return this
  }

  /**
   * Optionally show close shortcut next to Got It button
   */
  fun andShowCloseShortcut() : GotItTooltip {
    showCloseShortcut = true
    return this
  }

  /**
   * Set notification method that's called when actual <code>Balloon</code> is created.
   */
  fun setOnBalloonCreated(callback: (Balloon) -> Unit) : GotItTooltip {
    onBalloonCreated = callback
    return this
  }

  /**
   * Returns <code>true</code> if this tooltip can be shown at the given properties settings.
   */
  fun canShow() : Boolean = canShow("$PROPERTY_PREFIX.$id")

  /**
   * Show tooltip for the given component and point on the component.
   * If the component is showing (see <code>Component.isShowing</code>) and has non empty bounds then
   * the tooltip is show right away.
   * If the component is showing by has empty bounds (technically not visible) then tooltip is shown asynchronously
   * when component gets resized to non empty bounds.
   * If the component is not showing then tooltip is shown asynchronously when component is added to the hierarchy and
   * gets non empty bounds.
   */
  fun show(component: JComponent, pointProvider: (Component) -> Point) {
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
          }.also{ Disposer.register(this, Disposable { component.removeComponentListener(it) }) })
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
              }.also{ Disposer.register(this@GotItTooltip, Disposable { component.removeComponentListener(it) }) })
            }
          }

          override fun ancestorRemoved(ancestorEvent: AncestorEvent) {
            balloon?.let {
              it.hide(true)
              GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.AncestorRemoved)
            }
            balloon = null
          }
        }.also{ Disposer.register(this, Disposable { component.removeAncestorListener(it) }) })
      }
    }
  }

  /**
   * Bind the tooltip to action's presentation. Then <code>ActionToolbar</code> starts following ActionButton with
   * the tooltip if it can be shown. Term "follow" is used because ActionToolbar updates it's content and ActionButton's
   * JComponent showing status / location may change in time.
   */
  fun assignTo(presentation: Presentation, pointProvider: (Component) -> Point) {
    presentation.putClientProperty(PRESENTATION_KEY, ActionContext(this, pointProvider))
  }

  private fun showImpl(component: JComponent, pointProvider: (Component) -> Point) {
    if (canShow()) {
      val balloonProperty = UIUtil.getClientProperty(component, BALLOON_PROPERTY)
      if (balloonProperty == null) {
        val tracker = object : PositionTracker<Balloon> (component) {
          override fun recalculateLocation(balloon: Balloon): RelativePoint = RelativePoint(component, pointProvider(component))
        }
        balloon = createAndShow(tracker).also { UIUtil.putClientProperty(component, BALLOON_PROPERTY, it) }
      }
      else if (balloonProperty is BalloonImpl && balloonProperty.isVisible) {
        balloonProperty.revalidate()
      }
    }
    else {
      hideBalloon()
      chainFunction()
    }
  }

  /**
   * Chain this tooltip showing after another determined with the tooltip parameter.
   * It's possible to build a chain of got it tooltips shown on the screen.
   */
  fun showAfter(tooltip: GotItTooltip, component: JComponent, pointProvider: (Component) -> Point) {
    tooltip.chainFunction = { show(component, pointProvider) }
  }

  private fun followToolbarComponent(component: JComponent, toolbar: JComponent, pointProvider: (Component) -> Point) {
    if (canShow()) {
      component.addComponentListener(object : ComponentAdapter() {
        override fun componentMoved(event: ComponentEvent) {
          hideOrRepaint(event.component)
        }

        override fun componentResized(event: ComponentEvent) {
          if (balloon == null && !event.component.bounds.isEmpty && event.component.isShowing) {
            val tracker = PositionTracker.Static<Balloon>(RelativePoint(event.component, pointProvider(event.component)))
            balloon = createAndShow(tracker)
          }
          else {
            hideOrRepaint(event.component)
          }
        }
      }.also{ Disposer.register(this, Disposable { component.removeComponentListener(it) }) })

      toolbar.addAncestorListener(object : AncestorListenerAdapter() {
        override fun ancestorRemoved(event: AncestorEvent) {
          hideBalloon()
        }
      }.also{ Disposer.register(this, Disposable { component.removeAncestorListener(it) }) })
    }
  }

  private fun createAndShow(tracker: PositionTracker<Balloon>) : Balloon = createBalloon().also {
      val dispatcherDisposable = Disposer.newDisposable()
      Disposer.register(this, dispatcherDisposable)

      it.addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          HelpTooltip.setMasterPopupOpenCondition(tracker.component, null)
          UIUtil.putClientProperty(tracker.component as JComponent, BALLOON_PROPERTY, null)
          Disposer.dispose(dispatcherDisposable)

          if (event.isOk) {
            onGotIt("$PROPERTY_PREFIX.$id")
            chainFunction()
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

      it.show(tracker, position)
      onBalloonCreated(it)

      GotItUsageCollector.instance.logOpen(id, savedCount("$PROPERTY_PREFIX.$id") + 1)
    }

  private fun createBalloon() : Balloon {
    var button : JButton? = null
    val balloon = JBPopupFactory.getInstance().createBalloonBuilder(createContent { button = it }).
        setDisposable(this).
        setHideOnAction(false).
        setHideOnFrameResize(false).
        setHideOnKeyOutside(false).
        setBlockClicksThroughBalloon(true).
        setBorderColor(BORDER_COLOR).
        setCornerToPointerDistance(ARROW_SHIFT).
        setFillColor(BACKGROUND_COLOR).
        setPointerSize(JBUI.size(16, 8)).
        createBalloon().
      also {
        it.setAnimationEnabled(false)
        (it as BalloonImpl).setHideListener( Runnable {
          it.hide(true)
          GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.OutsideClick)
        } )
      }

    val collector = GotItUsageCollector.instance

    link?.apply{
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

  private fun createContent(buttonSupplier: (JButton) -> Unit) : JComponent {
    val panel = JPanel(GridBagLayout())
    val gc = GridBag()
    val left = if (icon != null) 8 else 0
    val column = if (icon != null) 1 else 0

    icon?.let { panel.add(JLabel(it), gc.nextLine().next().anchor(GridBagConstraints.BASELINE)) }

    if (header.isNotEmpty()) {
      if (icon == null) gc.nextLine()

      panel.add(JBLabel(HtmlChunk.raw(header).bold().wrapWith(HtmlChunk.html()).toString()),
                gc.setColumn(column).anchor(GridBagConstraints.LINE_START).insetLeft(left))
    }

    val builder = HtmlBuilder()
    builder.append(HtmlChunk.raw(text))
    shortcut?.let {
      builder.append(HtmlChunk.nbsp()).append(HtmlChunk.nbsp()).
              append(HtmlChunk.text(KeymapUtil.getShortcutText(it)).wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(SHORTCUT_COLOR))))
    }

    if (icon == null || header.isNotEmpty()) gc.nextLine()
    panel.add(LimitedWidthLabel(builder, maxWidth),
              gc.setColumn(column).anchor(GridBagConstraints.LINE_START).insets(if (header.isNotEmpty()) 5 else 0, left, 0, 0))

    link?.let {
      panel.add(it, gc.nextLine().setColumn(column).anchor(GridBagConstraints.LINE_START).insets(5, left, 0, 0))
    }

    if (timeout <= 0) {
      val button = JButton(buttonLabel).apply{
        isFocusable = false
        isOpaque = false
        putClientProperty("styleBorderless", true)
      }
      buttonSupplier(button)

      if (showCloseShortcut) {
        val buttonPanel = JPanel().apply{ isOpaque = false }
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(button)
        buttonPanel.add(Box.createHorizontalStrut(JBUIScale.scale(UIUtil.DEFAULT_HGAP)))

        @Suppress("HardCodedStringLiteral")
        val closeShortcut = JLabel(KeymapUtil.getShortcutText(CLOSE_ACTION_NAME)).apply { foreground = SHORTCUT_COLOR }
        buttonPanel.add(closeShortcut)

        panel.add(buttonPanel, gc.nextLine().setColumn(column).insets(11, left, 0, 0).anchor(GridBagConstraints.LINE_START))
      }
      else {
        panel.add(button, gc.nextLine().setColumn(column).insets(11, left, 0, 0).anchor(GridBagConstraints.LINE_START))
      }
    }

    panel.background = BACKGROUND_COLOR
    panel.border = PANEL_MARGINS

    return panel
  }

  override fun dispose() {
    hideBalloon()
  }

  private fun hideBalloon() {
    balloon?.hide(false)
    balloon = null
  }

  private fun hideOrRepaint(component: Component) {
    balloon?.let {
      if (component.bounds.isEmpty) {
        hideBalloon()
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

    private val PRESENTATION_KEY = Key<ActionContext>("$PROPERTY_PREFIX.presentation")
    private val BALLOON_PROPERTY = Key<Balloon>("$PROPERTY_PREFIX.balloon")

    private const val DEFAULT_TIMEOUT = 5000 // milliseconds
    private const val CLOSE_ACTION_NAME = "CloseGotItTooltip"
    private val MAX_WIDTH   = JBUIScale.scale(280)
    private val SHORTCUT_COLOR = JBColor.namedColor("ToolTip.shortcutForeground", JBColor(0x787878, 0x999999))
    private val BACKGROUND_COLOR = JBColor.namedColor("ToolTip.background", JBColor(0xf7f7f7, 0x474a4c))
    private val BORDER_COLOR = JBColor.namedColor("GotItTooltip.borderColor", JBColor(Color(0xcccccc), JBColor.namedColor("ToolTip.borderColor", 0x636569)))

    private val PANEL_MARGINS = JBUI.Borders.empty(7, 4, 9, 9)


    /**
     * Use this method for following an ActionToolbar component.
     */
    @JvmStatic
    fun followToolbarComponent(presentation: Presentation, component: JComponent, toolbar: JComponent) {
      presentation.getClientProperty(PRESENTATION_KEY)?.let {
        it.tooltip.followToolbarComponent(component, toolbar, it.pointProvider)
      }
    }

    // Frequently used point providers
    @JvmField
    val TOP_MIDDLE : (Component) -> Point = { Point(it.width / 2, 0) }

    @JvmField
    val LEFT_MIDDLE : (Component) -> Point = { Point(0, it.height / 2) }

    @JvmField
    val RIGHT_MIDDLE : (Component) -> Point = { Point(it.width, it.height / 2) }

    @JvmField
    val BOTTOM_MIDDLE : (Component) -> Point = { Point(it.width / 2, it.height) }
  }
}

private class LimitedWidthLabel(val htmlBuilder: HtmlBuilder, val limit: Int) : JLabel() {
  init {
    var view = BasicHTML.createHTMLView(this, htmlBuilder.wrapWith(HtmlChunk.html()).toString())
    var width = view.getPreferredSpan(View.X_AXIS)

    if (width < limit) {
      text = htmlBuilder.wrapWith(HtmlChunk.div()).wrapWith(HtmlChunk.html()).toString()
    }
    else {
      view = BasicHTML.createHTMLView(this, htmlBuilder.wrapWith(HtmlChunk.div().attr("width", limit)).wrapWith(HtmlChunk.html()).toString())
      width = rows(view).maxOf { it.getPreferredSpan(View.X_AXIS) }
      text = htmlBuilder.wrapWith(HtmlChunk.div().attr("width", width.toInt())).wrapWith(HtmlChunk.html()).toString()
    }
  }

  private fun rows(root: View) : Collection<View> {
    return ArrayList<View>().also { visit(root, it) }
  }

  private fun visit(view: View, collection: MutableCollection<View>) {
    val cname : String? = view.javaClass.canonicalName
    cname?.let { if (it.contains("ParagraphView.Row")) collection.add(view) }

    for (i in 0 until view.viewCount) {
      visit(view.getView(i), collection)
    }
  }
}