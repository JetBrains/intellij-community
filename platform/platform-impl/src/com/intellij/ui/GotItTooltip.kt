// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
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
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionListener
import java.net.URL
import javax.swing.*
import javax.swing.event.AncestorEvent

class PointPosition(val point: RelativePoint, val position: Balloon.Position)

class GotItTooltip(@NonNls val id: String, @Nls val text: String, val disposable: Disposable) {
  @Nls
  private var header : String = ""

  @NlsSafe
  private var shortcut: String = ""

  @Nls
  private var buttonLabel: String = IdeBundle.message("got.it.button.name")

  private var icon: Icon? = null
  private var timeout : Int = -1
  private var link : LinkLabel<Unit>? = null
  private var linkAction : () -> Unit = {}

  private var canShow : (String) -> Boolean = { !PropertiesComponent.getInstance().isTrueValue(PROPERTY_PREFIX + it) }
  private var onGotIt : (String) -> Unit = { PropertiesComponent.getInstance().setValue(PROPERTY_PREFIX + it, true) }

  private val alarm = Alarm()

  fun withHeader(@Nls header: String) : GotItTooltip {
    this.header = header
    return this
  }

  fun withShortcut(@NonNls shortcut: String) : GotItTooltip {
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

  fun withTimeout(timeout : Int = DEFAULT_TIMEOUT) : GotItTooltip {
    if (timeout > 0) {
      this.timeout = timeout
    }
    return this
  }

  fun withLink(@Nls linkLabel: String, action : () -> Unit) : GotItTooltip {
    link = LinkLabel<Unit>(linkLabel, null)
    linkAction = action
    return this
  }

  fun withLink(@Nls linkLabel: String, action : Runnable) : GotItTooltip {
    return withLink(linkLabel) { action.run() }
  }

  fun withBrowserLink(@Nls linkLabel: String, url : URL) : GotItTooltip {
    link = LinkLabel<Unit>(linkLabel, AllIcons.Ide.External_link_arrow).apply{ horizontalTextPosition = SwingConstants.LEFT }
    linkAction = { BrowserUtil.browse(url) }
    return this
  }

  fun showFor(component: JComponent, pointPosition: (AncestorEvent) -> PointPosition) {
    var balloon : Balloon? = null

    component.addAncestorListener(object : AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent?) {
        event?.let {
          if (canShow(id)) {
            val pp = pointPosition(event)
            balloon = createBalloon().also {
              it.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                  if (event.isOk) onGotIt(id)
                }
              })

              it.show(pp.point, pp.position)
            }
          }
        }
      }

      override fun ancestorRemoved(event: AncestorEvent?) {
        balloon?.hide(false)
        balloon = null
      }
    })

    HelpTooltip.setMasterPopupOpenCondition(component) {
      balloon?.isDisposed ?: true
    }
  }

  private fun createBalloon() : Balloon {
    var button : JButton? = null
    val balloon = JBPopupFactory.getInstance().createBalloonBuilder(createContent{ button = it }).
        setDisposable(disposable).
        setHideOnClickOutside(false).
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
      addActionListener(ActionListener { balloon.hide(true) })
    }

    if (timeout > 0) {
      alarm.cancelAllRequests()
      alarm.addRequest({ balloon.hide(true) }, timeout)
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

      panel.add(JBLabel(HtmlChunk.text(header).bold().wrapWith(HtmlChunk.html()).toString()),
                gc.setColumn(column).anchor(GridBagConstraints.LINE_START).insetLeft(left))
    }

    val builder = HtmlBuilder()
    builder.append(HtmlChunk.text(text))
    if (shortcut.isNotEmpty()) {
      builder.append(HtmlChunk.nbsp()).append(HtmlChunk.nbsp()).
              append(HtmlChunk.text(shortcut).wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(SHORTCUT_COLOR))))
    }

    panel.add(JBLabel(builder.wrapWith(HtmlChunk.div().attr("width", MAX_WIDTH)).wrapWith(HtmlChunk.html()).toString()),
              gc.nextLine().setColumn(column).anchor(GridBagConstraints.LINE_START).
              insets(if (header.isNotEmpty()) 5 else 0, left, 0, 0))

    link?.let {
      panel.add(it, gc.nextLine().setColumn(column).anchor(GridBagConstraints.LINE_START).insets(5, left, 0, 0))
    }

    if (timeout <= 0) {
      val button = JButton(buttonLabel).apply{
        isFocusable = false
        isOpaque = false
      }
      buttonSupplier(button)
      panel.add(button, gc.nextLine().setColumn(column).insets(11, left - button.insets.left, 0, 0).anchor(GridBagConstraints.LINE_START))
    }

    panel.background = BACKGROUND_COLOR

    return panel
  }

  companion object {
    const val DEFAULT_TIMEOUT = 5000 // milliseconds
    const val PROPERTY_PREFIX = "GotItTooltip."

    val ARROW_SHIFT = JBUIScale.scale(20) + Registry.intValue("ide.balloon.shadow.size") + BalloonImpl.ARC.get()
    val MAX_WIDTH   = JBUIScale.scale(280)

    val SHORTCUT_COLOR = JBColor.namedColor("ToolTip.shortcutForeground", JBColor(0x787878, 0x999999))
    val BACKGROUND_COLOR = JBColor.namedColor("ToolTip.background", JBColor(0xf7f7f7, 0x474a4c))
    val BORDER_COLOR = JBColor.namedColor("ToolTip.borderColor", JBColor(0xadadad, 0x636569))
  }
}