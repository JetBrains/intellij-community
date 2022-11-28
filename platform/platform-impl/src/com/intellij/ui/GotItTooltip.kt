// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.HelpTooltip
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
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.net.URL
import javax.swing.*
import javax.swing.event.AncestorEvent
import javax.swing.text.*

@Service(Service.Level.APP)
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
 * The `id` is a unique identifier for the tooltip that will be used to store the tooltip state in [PropertiesComponent].
 * Identifier has the following format: `place.where.used` (lowercase words separated with dots).
 *
 * Got It tooltip usage statistics can be properly gathered if its identifier prefix is registered in
 * `plugin.xml` (`PlatformExtensions.xml`) with `com.intellij.statistics.gotItTooltipAllowlist` extension point.
 * Prefix can cover a whole class of different Got It tooltips. If the prefix is shorter than the whole ID, then all different
 * tooltip usages will be reported in one category described by the prefix.
 *
 * The description of the tooltip can contain inline shortcuts, icons and links.
 * See [GotItTextBuilder] doc for more info.
 */
class GotItTooltip(@NonNls val id: String,
                   textSupplier: GotItTextBuilder.() -> @Nls String,
                   parentDisposable: Disposable? = null) : ToolbarActionTracker<Balloon>() {
  private val gotItBuilder = GotItComponentBuilder(textSupplier)

  private var timeout: Int = -1
  private var maxCount = 1
  private var onBalloonCreated: (Balloon) -> Unit = {}

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

  constructor(@NonNls id: String,
              @Nls text: String,
              parentDisposable: Disposable? = null)
    : this(id, { text }, parentDisposable)

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
   * Add an optional image above the header or description
   */
  fun withImage(image: Icon): GotItTooltip {
    gotItBuilder.withImage(image)
    return this
  }

  /**
   * Add an optional header to the tooltip.
   */
  fun withHeader(@Nls header: String): GotItTooltip {
    gotItBuilder.withHeader(header)
    return this
  }

  /**
   * Set preferred tooltip position relatively to the owner component.
   */
  fun withPosition(position: Balloon.Position): GotItTooltip {
    this.position = position
    return this
  }

  /**
   * Add optional shortcut after mandatory description (text).
   */
  fun withShortcut(shortcut: Shortcut): GotItTooltip {
    gotItBuilder.withShortcut(shortcut)
    return this
  }

  /**
   * Set alternative button text instead of default "Got It".
   */
  fun withButtonLabel(@Nls label: String): GotItTooltip {
    gotItBuilder.withButtonLabel(label)
    return this
  }

  /**
   * Add optional icon on the left of the header or description.
   * Is not compatible with step number.
   *
   * @throws IllegalStateException if step number already specified using [withStepNumber].
   */
  fun withIcon(icon: Icon): GotItTooltip {
    gotItBuilder.withIcon(icon)
    return this
  }

  /**
   * Add optional step number on the left of the header or description.
   * The step will be rendered with one zero predecessor if step number is lower than 10.
   * For example: 01, 02, 10, 12.
   * The step number should be in the range [1, 99].
   * Is not compatible with icon.
   *
   * @throws IllegalStateException if icon already specified using [withIcon].
   * @throws IllegalArgumentException if [step] is not in a range [1, 99].
   */
  fun withStepNumber(step: Int): GotItTooltip {
    gotItBuilder.withStepNumber(step)
    return this
  }

  /**
   * Set the close timeout. If set, then the tooltip appears without the "Got It" button.
   */
  @JvmOverloads
  fun withTimeout(timeout: Int = DEFAULT_TIMEOUT): GotItTooltip {
    if (timeout > 0) {
      this.timeout = timeout
      gotItBuilder.showButton(false)
    }
    return this
  }

  /**
   * Limit tooltip body width to the given value. By default, it's limited to [GotItComponentBuilder.MAX_WIDTH] pixels.
   */
  fun withMaxWidth(width: Int): GotItTooltip {
    gotItBuilder.withMaxWidth(width)
    return this
  }

  /**
   * Add an optional link to the tooltip.
   */
  fun withLink(@Nls linkLabel: String, action: () -> Unit): GotItTooltip {
    gotItBuilder.withLink(linkLabel, action)
    return this
  }

  /**
   * Add an optional link to the tooltip. Java version.
   */
  fun withLink(@Nls linkLabel: String, action: Runnable): GotItTooltip {
    return withLink(linkLabel) { action.run() }
  }

  /**
   * Add an optional browser link to the tooltip. Link is rendered with arrow icon.
   */
  fun withBrowserLink(@Nls linkLabel: String, url: URL): GotItTooltip {
    gotItBuilder.withBrowserLink(linkLabel, url)
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
    gotItBuilder.withContrastColors(contrastColors)
    return this
  }

  /**
   * Show close shortcut next to the "Got It" button.
   */
  fun andShowCloseShortcut(): GotItTooltip {
    gotItBuilder.showCloseShortcut(true)
    return this
  }

  /**
   * Set the notification method that's called when actual [Balloon] is created.
   */
  fun setOnBalloonCreated(callback: (Balloon) -> Unit): GotItTooltip {
    onBalloonCreated = callback
    return this
  }

  /**
   * Returns `true` if this tooltip can be shown at the given properties settings.
   */
  override fun canShow(): Boolean = showCondition("$PROPERTY_PREFIX.$id")

  /**
   * Show tooltip for the given component and point to the component.
   *
   * If the component is showing (see [Component.isShowing]) and has not empty bounds,
   * then the tooltip is shown right away.
   *
   * If the component is showing but has empty bounds (technically not visible),
   * then tooltip is shown asynchronously when the component gets resized to not empty bounds.
   *
   * If the component is not showing, then tooltip is shown asynchronously when component is added to the hierarchy
   * and gets not empty bounds.
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
      val balloonProperty = ClientProperty.get(component, BALLOON_PROPERTY)
      if (balloonProperty == null) {
        balloon = createAndShow(component, pointProvider).also { ClientProperty.put(component, BALLOON_PROPERTY, it) }
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
          ClientProperty.put(tracker.component as JComponent, BALLOON_PROPERTY, null)
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
        if (e is KeyEvent && KeymapUtil.isEventForAction(e, GotItComponentBuilder.CLOSE_ACTION_NAME)) {
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
    val balloon = gotItBuilder
      .onButtonClick { GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.ButtonClick) }
      .onLinkClick { GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.LinkClick) }
      .build(parentDisposable = this)

    if (timeout > 0) {
      alarm.cancelAllRequests()
      alarm.addRequest({
                         balloon.hide(true)
                         GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.Timeout)
                       }, timeout)
    }

    return balloon
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
    const val PROPERTY_PREFIX = "got.it.tooltip"

    private val BALLOON_PROPERTY = Key<Balloon>("$PROPERTY_PREFIX.balloon")

    private const val DEFAULT_TIMEOUT = 5000 // milliseconds

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