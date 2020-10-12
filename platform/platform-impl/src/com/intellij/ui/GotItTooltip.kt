// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
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
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.net.URL
import javax.swing.*
import javax.swing.event.AncestorEvent
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View

class PointPosition(val point: RelativePoint, val position: Balloon.Position)

class GotItTooltip(@NonNls val id: String, @Nls val text: String, val disposable: Disposable) {
  @Nls
  private var header : String = ""

  @NlsSafe
  private var shortcut: Shortcut? = null

  @Nls
  private var buttonLabel: String = IdeBundle.message("got.it.button.name")

  private var icon: Icon? = null
  private var timeout : Int = -1
  private var link : LinkLabel<Unit>? = null
  private var linkAction : () -> Unit = {}
  private var maxWidth = MAX_WIDTH
  private var showCloseShortcut = false

  private var canShow : (String) -> Boolean = { !PropertiesComponent.getInstance().isTrueValue(PROPERTY_PREFIX + it) }
  private var onGotIt : (String) -> Unit = { PropertiesComponent.getInstance().setValue(PROPERTY_PREFIX + it, true) }

  private val alarm = Alarm()

  fun withHeader(@Nls header: String) : GotItTooltip {
    this.header = header
    return this
  }

  fun withShortcut(shortcut: Shortcut) : GotItTooltip {
    this.shortcut = shortcut
    return this
  }

  fun withButtonLabel(@Nls label: String) : GotItTooltip {
    this.buttonLabel = label
    return this
  }

  fun withIcon(icon: Icon) : GotItTooltip {
    this.icon = icon
    return this
  }

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

  fun withLink(@Nls linkLabel: String, action: () -> Unit) : GotItTooltip {
    link = LinkLabel<Unit>(linkLabel, null)
    linkAction = action
    return this
  }

  fun withLink(@Nls linkLabel: String, action: Runnable) : GotItTooltip {
    return withLink(linkLabel) { action.run() }
  }

  fun withBrowserLink(@Nls linkLabel: String, url: URL) : GotItTooltip {
    link = LinkLabel<Unit>(linkLabel, AllIcons.Ide.External_link_arrow).apply{ horizontalTextPosition = SwingConstants.LEFT }
    linkAction = { BrowserUtil.browse(url) }
    return this
  }

  fun andShowCloseShortcut() : GotItTooltip {
    showCloseShortcut = true
    return this
  }

  fun showFor(component: JComponent, positionProvider: (Component) -> PointPosition) {
    var balloon : Balloon? = null

    component.addAncestorListener(object : AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent?) {
        if (canShow(id) && event != null) {
          val pointPosition = positionProvider(event.component)
          balloon = createAndShow(pointPosition.point, pointPosition.position)
        }
      }

      override fun ancestorRemoved(event: AncestorEvent?) {
        balloon?.hide()
        balloon = null
      }
    })
  }

  fun showAt(point: RelativePoint, position: Balloon.Position) : Balloon? = if (canShow(id)) createAndShow(point, position) else null

  private fun createAndShow(point: RelativePoint, position: Balloon.Position) : Balloon = createBalloon().also {
      val dispatcherDisposable = Disposer.newDisposable()
      Disposer.register(disposable, dispatcherDisposable)

      it.addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          onGotIt(id)
          HelpTooltip.setMasterPopupOpenCondition(point.component, null)
          Disposer.dispose(dispatcherDisposable)
        }
      })

      IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.EventDispatcher { e ->
        if (e is KeyEvent && KeymapUtil.isEventForAction(e, CLOSE_ACTION_NAME)) {
          it.hide()
          true
        }
        else false
      }, dispatcherDisposable)

      HelpTooltip.setMasterPopupOpenCondition(point.component) {
        it.isDisposed
      }

      it.show(point, position)
    }

  private fun createBalloon() : Balloon {
    var button : JButton? = null
    val balloon = JBPopupFactory.getInstance().createBalloonBuilder(createContent { button = it }).
        setDisposable(disposable).
        setHideOnClickOutside(true).
        setHideOnAction(false).
        setHideOnFrameResize(false).
        setHideOnKeyOutside(false).
        setBlockClicksThroughBalloon(true).
        setBorderColor(BORDER_COLOR).
        setCornerToPointerDistance(ARROW_SHIFT).
        setFillColor(BACKGROUND_COLOR).
        createBalloon().
      apply { setAnimationEnabled(false) }

    link?.apply{
      setListener(LinkListener { _, _ ->
        linkAction()
        balloon.hide(true)
      }, null)
    }

    button?.apply {
      addActionListener(ActionListener { balloon.hide() })
    }

    if (timeout > 0) {
      alarm.cancelAllRequests()
      alarm.addRequest({ balloon.hide() }, timeout)
    }

    return balloon
  }

  private fun createContent(buttonSupplier: (JButton) -> Unit) : JComponent {
    val panel = JPanel(GridBagLayout())
    val gc = GridBag()
    val left = if (icon != null) 8 else 0
    val column = if (icon != null) 1 else 0

    icon?.let { panel.add(JLabel(it), gc.nextLine().next()) }

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

    panel.add(LimitedWidthLabel(builder, maxWidth),
              gc.nextLine().setColumn(column).anchor(GridBagConstraints.LINE_START).insets(if (header.isNotEmpty()) 5 else 0, left, 0, 0))

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

    return panel
  }

  companion object {
    const val DEFAULT_TIMEOUT = 5000 // milliseconds
    const val PROPERTY_PREFIX = "GotItTooltip."
    const val CLOSE_ACTION_NAME = "CloseGotItTooltip"

    val ARROW_SHIFT = JBUIScale.scale(20) + Registry.intValue("ide.balloon.shadow.size") + BalloonImpl.ARC.get()
    val MAX_WIDTH   = JBUIScale.scale(280)

    val SHORTCUT_COLOR = JBColor.namedColor("ToolTip.shortcutForeground", JBColor(0x787878, 0x999999))
    val BACKGROUND_COLOR = JBColor.namedColor("ToolTip.background", JBColor(0xf7f7f7, 0x474a4c))
    val BORDER_COLOR = JBColor.namedColor("ToolTip.borderColor", JBColor(0xadadad, 0x636569))
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