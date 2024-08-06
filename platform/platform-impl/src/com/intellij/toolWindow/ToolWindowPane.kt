// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("GraphicsSetClipInspection")

package com.intellij.toolWindow

import com.intellij.ide.RemoteDesktopService
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl.Companion.getAdjustedRatio
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl.Companion.getRegisteredMutableInfoOrLogError
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.DevicePoint
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextCache
import com.intellij.util.IJSwingUtilities
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.awt.*
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference
import java.util.concurrent.Future
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLayeredPane
import javax.swing.LayoutFocusTraversalPolicy
import kotlin.math.max
import kotlin.math.min

private val LOG = logger<ToolWindowPane>()

/**
 * This panel contains all tool stripes and JLayeredPane at the center area. All tool windows are located inside this layered pane.
 */
class ToolWindowPane private constructor(
  frame: JFrame,
  val paneId: String,
  @field:JvmField internal val buttonManager: ToolWindowButtonManager,
) : JLayeredPane(), UISettingsListener {
  companion object {
    const val TEMPORARY_ADDED: String = "TEMPORARY_ADDED"

    // the size of the topmost 'resize' area when toolwindow caption is used for both resize and drag
    internal val headerResizeArea: Int
      get() = JBUI.scale(Registry.intValue("ide.new.tool.window.resize.area.height", 14, 1, 26))

    private fun normalizeWeight(weight: Float): Float {
      if (weight <= 0) {
        return WindowInfoImpl.DEFAULT_WEIGHT
      }
      return if (weight >= 1) 1 - WindowInfoImpl.DEFAULT_WEIGHT else weight
    }

    internal fun log() = LOG

    internal fun create(frame: JFrame, coroutineScope: CoroutineScope, paneId: String, buttonManager: ToolWindowButtonManager): ToolWindowPane {
      return ToolWindowPane(frame, paneId, buttonManager).also { pane ->
        val app = ApplicationManager.getApplication()
        app.messageBus.connect(coroutineScope).subscribe(LafManagerListener.TOPIC, LafManagerListener { pane.isLookAndFeelUpdated = true })
      }
    }

    internal fun create(frame: JFrame, coroutineScope: CoroutineScope, paneId: String): ToolWindowPane {
      val buttonManager = createButtonManager(paneId)
      return create(frame, coroutineScope, paneId, buttonManager)
    }

    // this is a method strictly for use in tests
    // will not react to LaF changes, so if you want to test this - use one of the methods with coroutine scope
    @Internal
    @VisibleForTesting
    fun create(frame: JFrame, paneId: String): ToolWindowPane {
      val buttonManager = createButtonManager(paneId)
      return ToolWindowPane(frame, paneId, buttonManager)
    }

    private fun createButtonManager(paneId: String): ToolWindowButtonManager {
      val buttonManager: ToolWindowButtonManager
      if (ExperimentalUI.isNewUI()) {
        buttonManager = ToolWindowPaneNewButtonManager(paneId)
      }
      else {
        buttonManager = ToolWindowPaneOldButtonManager(paneId)
      }
      return buttonManager
    }
  }

  private var isLookAndFeelUpdated = false
  private var state = ToolWindowPaneState()

  internal val frame: JFrame

  /**
   * This panel is the layered pane where all sliding tool windows are located. The DEFAULT
   * layer contains splitters. The PALETTE layer contains all sliding tool windows.
   */
  private val layeredPane: FrameLayeredPane

  /*
   * Splitters
   */
  private val verticalSplitter: ThreeComponentsSplitter
  private val horizontalSplitter: ThreeComponentsSplitter
  private var isWideScreen: Boolean
  private var leftHorizontalSplit: Boolean
  private var rightHorizontalSplit: Boolean

  private val disposable = Disposer.newDisposable()

  init {
    isOpaque = false
    this.frame = frame

    name = paneId

    // splitters
    verticalSplitter = ThreeComponentsSplitter(true)
    val registryValue = Registry.get("ide.mainSplitter.min.size")
    registryValue.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        updateInnerMinSize(value)
      }
    }, disposable)
    verticalSplitter.dividerWidth = 0
    verticalSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"))
    verticalSplitter.background = JBColor.GRAY
    horizontalSplitter = ThreeComponentsSplitter(false)
    horizontalSplitter.dividerWidth = 0
    horizontalSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"))
    horizontalSplitter.background = JBColor.GRAY
    updateInnerMinSize(registryValue)
    val uiSettings = UISettings.getInstance()
    isWideScreen = uiSettings.wideScreenSupport
    leftHorizontalSplit = uiSettings.leftHorizontalSplit
    rightHorizontalSplit = uiSettings.rightHorizontalSplit
    if (isWideScreen) {
      horizontalSplitter.innerComponent = verticalSplitter
    }
    else {
      verticalSplitter.innerComponent = horizontalSplitter
    }
    updateToolStripesVisibility(uiSettings)

    // layered pane
    layeredPane = FrameLayeredPane(if (isWideScreen) horizontalSplitter else verticalSplitter, frame = frame)

    // compose layout
    buttonManager.setupToolWindowPane(this)
    add(layeredPane, DEFAULT_LAYER, -1)
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    ToolWindowDragHelper(disposable, this).start()
    if (Registry.`is`("ide.allow.split.and.reorder.in.tool.window")) {
      ToolWindowInnerDragHelper(disposable, this).start()
    }
  }

  override fun removeNotify() {
    super.removeNotify()

    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      Disposer.dispose(disposable)
    }
  }

  private fun updateInnerMinSize(value: RegistryValue) {
    val minSize = max(0, min(100, value.asInteger()))
    verticalSplitter.minSize = JBUIScale.scale(minSize)
    horizontalSplitter.minSize = JBUIScale.scale(minSize)
  }

  override fun doLayout() {
    buttonManager.layout(size, layeredPane)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateToolStripesVisibility(uiSettings)
    updateLayout(uiSettings)
  }

  /**
   * @param dirtyMode if `true` then JRootPane will not be validated and repainted after adding the decorator.
   * Moreover, in this (dirty) mode, animation doesn't work.
   */
  fun addDecorator(decorator: JComponent, info: WindowInfo, dirtyMode: Boolean, manager: ToolWindowManagerImpl) {
    if (info.isDocked) {
      val side = !info.isSplit
      val anchor = info.anchor
      val sideInfo = manager.getDockedInfoAt(paneId, anchor, side)
      if (sideInfo == null) {
        setComponent(decorator, anchor, normalizeWeight(info.weight))
        if (!dirtyMode) {
          layeredPane.validate()
          layeredPane.repaint()
        }
      }
      else {
        addAndSplitDockedComponentCmd(decorator, info, dirtyMode, manager)
      }
    }
    else if (info.type == ToolWindowType.SLIDING) {
      addSlidingComponent(decorator, info, dirtyMode)
    }
    else {
      throw IllegalArgumentException("Unknown window type: ${info.type}")
    }
  }

  fun removeDecorator(info: WindowInfo, component: JComponent?, dirtyMode: Boolean, manager: ToolWindowManagerImpl) {
    if (info.type != ToolWindowType.DOCKED) {
      if (info.type == ToolWindowType.SLIDING) {
        component?.let { removeSlidingComponent(it, info, dirtyMode) }
      }
    }
    else if (component != null && component.isShowing) {
      val anchor = info.anchor
      val sideInfo = manager.getDockedInfoAt(paneId, anchor, !info.isSplit)
      if (sideInfo == null) {
        setComponent(null, anchor, 0f)
      }
      else {
        val c = getComponentAt(anchor)
        if (c is Splitter) {
          val component1 = (if (info.isSplit) c.firstComponent else c.secondComponent) as InternalDecoratorImpl
          state.addSplitProportion(info, component1, c)
          setComponent(component1, anchor, getRegisteredMutableInfoOrLogError(component1).weight)
          // detach removed component from the splitter
          // makes a difference for rem-dev scenarios, see BackendServerToolWindowManager.ensureShowing
          if (info.isSplit) {
            c.secondComponent = null
          }
          else {
            c.firstComponent = null
          }
        }
        else {
          setComponent(null, anchor, 0f)
        }
      }

      if (!dirtyMode) {
        layeredPane.validate()
        layeredPane.repaint()
      }
    }
  }

  fun getLayeredPane(): JComponent = layeredPane

  fun validateAndRepaint() {
    layeredPane.validate()
    layeredPane.repaint()
    buttonManager.validateAndRepaint()
  }

  private fun setComponent(component: JComponent?, anchor: ToolWindowAnchor, weight: Float) {
    when (anchor) {
      ToolWindowAnchor.TOP -> verticalSplitter.firstComponent = component
      ToolWindowAnchor.LEFT -> horizontalSplitter.firstComponent = component
      ToolWindowAnchor.BOTTOM -> verticalSplitter.lastComponent = component
      ToolWindowAnchor.RIGHT -> horizontalSplitter.lastComponent = component
      else -> LOG.error("unknown anchor: $anchor")
    }
    setWeight(anchor, weight)
  }

  private val setAnchorWeightFutures = hashMapOf<ToolWindowAnchor, Future<*>>()

  internal fun setWeight(anchor: ToolWindowAnchor, weight: Float) {
    setAnchorWeightFutures.remove(anchor)?.cancel(false)
    // can be null in tests
    val rootPane = rootPane
    val size = rootPane?.size ?: Dimension()
    if (rootPane != null && size.height == 0 && size.width == 0) {
      if (LOG.isDebugEnabled) {
        LOG.debug("Postponing setting the weight of the anchor $anchor because the root pane size is $size")
      }
      setAnchorWeightFutures[anchor] = UIUtil.runOnceWhenResized(rootPane) {
        if (LOG.isDebugEnabled) {
          LOG.debug("Retrying to set the weight of the anchor $anchor on pane resize")
        }
        setWeight(anchor, weight)
      }
      return
    }
    val anchorSize = when (anchor) {
      ToolWindowAnchor.TOP -> (size.getHeight() * weight).toInt()
      ToolWindowAnchor.LEFT -> (size.getWidth() * weight).toInt()
      ToolWindowAnchor.BOTTOM -> (size.getHeight() * weight).toInt()
      ToolWindowAnchor.RIGHT -> (size.getWidth() * weight).toInt()
      else -> {
        LOG.error("unknown anchor: $anchor")
        return
      }
    }
    when (anchor) {
      ToolWindowAnchor.TOP -> verticalSplitter.firstSize = anchorSize
      ToolWindowAnchor.LEFT -> horizontalSplitter.firstSize = anchorSize
      ToolWindowAnchor.BOTTOM -> verticalSplitter.lastSize = anchorSize
      ToolWindowAnchor.RIGHT -> horizontalSplitter.lastSize = anchorSize
      else -> {
        LOG.error("unknown anchor: $anchor")
        return
      }
    }
    if (LOG.isDebugEnabled) {
      LOG.debug("Set the size of the anchor $anchor to $anchorSize based on the root pane size $size and weight $weight")
    }
  }

  internal fun setSideWeight(window: ToolWindowImpl, sideWeight: Float) {
    if (window.type != ToolWindowType.DOCKED) {
      return
    }
    val splitter = getComponentAt(window.anchor)
    if (splitter !is Splitter) {
      return
    }
    val proportion = if (window.windowInfo.isSplit) {
      normalizeWeight(1.0f - sideWeight)
    } else {
      normalizeWeight(sideWeight)
    }
    splitter.proportion = proportion
    if (LOG.isDebugEnabled) {
      LOG.debug("Set the proportion of the size splitter ${window.anchor} to $proportion " +
                "because ${window.id} is the ${if (window.windowInfo.isSplit) "last" else "first"} in the splitter " +
                "and has side weight of $sideWeight")
    }
  }

  private fun getComponentAt(anchor: ToolWindowAnchor): JComponent? {
    return when (anchor) {
      ToolWindowAnchor.TOP -> verticalSplitter.firstComponent
      ToolWindowAnchor.LEFT -> horizontalSplitter.firstComponent
      ToolWindowAnchor.BOTTOM -> verticalSplitter.lastComponent
      ToolWindowAnchor.RIGHT -> horizontalSplitter.lastComponent
      else -> {
        LOG.error("unknown anchor: $anchor")
        null
      }
    }
  }

  @RequiresEdt
  fun setDocumentComponent(component: JComponent?) {
    (if (isWideScreen) verticalSplitter else horizontalSplitter).innerComponent = component
  }

  @RequiresEdt
  fun getDocumentComponent(): JComponent? {
    return (if (isWideScreen) verticalSplitter else horizontalSplitter).innerComponent
  }

  private fun updateToolStripesVisibility(uiSettings: UISettings) {
    val showButtons = !uiSettings.hideToolStripes && !uiSettings.presentationMode
    if (buttonManager.updateToolStripesVisibility(showButtons, state)) {
      revalidate()
      repaint()
    }
  }

  val bottomHeight: Int
    get() = buttonManager.getBottomHeight()
  val isBottomSideToolWindowsVisible: Boolean
    get() = getComponentAt(ToolWindowAnchor.BOTTOM) != null

  internal fun getStripeFor(devicePoint: DevicePoint, preferred: AbstractDroppableStripe): AbstractDroppableStripe? {
    return buttonManager.getStripeFor(devicePoint, preferred, this)
  }

  fun stretchWidth(window: ToolWindow, value: Int) {
    stretch(window, value)
  }

  fun stretchHeight(window: ToolWindow, value: Int) {
    stretch(window, value)
  }

  private fun stretch(window: ToolWindow, value: Int) {
    val pair = findResizerAndComponent(window) ?: return
    val vertical = window.anchor == ToolWindowAnchor.TOP || window.anchor == ToolWindowAnchor.BOTTOM
    val actualSize = (if (vertical) pair.second.height else pair.second.width) + value
    val first = window.anchor == ToolWindowAnchor.LEFT || window.anchor == ToolWindowAnchor.TOP
    val maxValue = if (vertical) verticalSplitter.getMaxSize(first) else horizontalSplitter.getMaxSize(first)
    val minValue = if (vertical) verticalSplitter.getMinSize(first) else horizontalSplitter.getMinSize(first)
    pair.first.setSize(max(minValue, min(maxValue, actualSize)))
  }

  private fun findResizerAndComponent(window: ToolWindow): Pair<Resizer, Component>? {
    if (!window.isVisible) {
      return null
    }

    var resizer: Resizer? = null
    var component: Component? = null
    if (window.type == ToolWindowType.DOCKED) {
      component = getComponentAt(window.anchor)
      if (component != null) {
        resizer = if (window.anchor.isHorizontal) {
          if (verticalSplitter.firstComponent === component) Resizer.Splitter.FirstComponent(verticalSplitter)
          else Resizer.Splitter.LastComponent(verticalSplitter)
        }
        else {
          if (horizontalSplitter.firstComponent === component) Resizer.Splitter.FirstComponent(horizontalSplitter)
          else Resizer.Splitter.LastComponent(horizontalSplitter)
        }
      }
    }
    else if (window.type == ToolWindowType.SLIDING) {
      component = window.component
      while (component != null) {
        if (component.parent === layeredPane) {
          break
        }
        component = component.parent
      }
      if (component != null) {
        resizer = when (window.anchor) {
          ToolWindowAnchor.TOP -> Resizer.LayeredPane.Top(component)
          ToolWindowAnchor.BOTTOM -> Resizer.LayeredPane.Bottom(component)
          ToolWindowAnchor.LEFT -> Resizer.LayeredPane.Left(component)
          ToolWindowAnchor.RIGHT -> Resizer.LayeredPane.Right(component)
          else -> null
        }
      }
    }
    return if (resizer == null) null else Pair(resizer, component)
  }

  private fun updateLayout(uiSettings: UISettings) {
    if (isWideScreen != uiSettings.wideScreenSupport) {
      val documentComponent = (if (isWideScreen) verticalSplitter else horizontalSplitter).innerComponent
      isWideScreen = uiSettings.wideScreenSupport
      if (isWideScreen) {
        verticalSplitter.innerComponent = null
        horizontalSplitter.innerComponent = verticalSplitter
      }
      else {
        horizontalSplitter.innerComponent = null
        verticalSplitter.innerComponent = horizontalSplitter
      }
      layeredPane.remove(if (isWideScreen) verticalSplitter else horizontalSplitter)
      layeredPane.add(if (isWideScreen) horizontalSplitter else verticalSplitter, DEFAULT_LAYER, -1)
      setDocumentComponent(documentComponent)
    }
    if (leftHorizontalSplit != uiSettings.leftHorizontalSplit) {
      val component = getComponentAt(ToolWindowAnchor.LEFT)
      if (component is Splitter) {
        val firstInfo = getRegisteredMutableInfoOrLogError((component.firstComponent as InternalDecoratorImpl))
        val secondInfo = getRegisteredMutableInfoOrLogError((component.secondComponent as InternalDecoratorImpl))
        setComponent(component = component,
                     anchor = ToolWindowAnchor.LEFT,
                     weight = if (ToolWindowAnchor.LEFT.isSplitVertically) firstInfo.weight else firstInfo.weight + secondInfo.weight)
      }
      leftHorizontalSplit = uiSettings.leftHorizontalSplit
    }
    if (rightHorizontalSplit != uiSettings.rightHorizontalSplit) {
      val component = getComponentAt(ToolWindowAnchor.RIGHT)
      if (component is Splitter) {
        val firstInfo = getRegisteredMutableInfoOrLogError((component.firstComponent as InternalDecoratorImpl))
        val secondInfo = getRegisteredMutableInfoOrLogError((component.secondComponent as InternalDecoratorImpl))
        setComponent(component = component,
                     anchor = ToolWindowAnchor.RIGHT,
                     weight = if (ToolWindowAnchor.RIGHT.isSplitVertically) firstInfo.weight else firstInfo.weight + secondInfo.weight)
      }
      rightHorizontalSplit = uiSettings.rightHorizontalSplit
    }
  }

  fun isMaximized(window: ToolWindow): Boolean = state.isMaximized(window)

  fun setMaximized(toolWindow: ToolWindow, maximized: Boolean) {
    val resizerAndComponent = findResizerAndComponent(toolWindow) ?: return
    if (maximized) {
      val size = if (toolWindow.anchor.isHorizontal) resizerAndComponent.second.height else resizerAndComponent.second.width
      stretch(toolWindow, Short.MAX_VALUE.toInt())
      state.maximizedProportion = Pair(toolWindow, size)
    }
    else {
      val maximizedProportion = state.maximizedProportion
      LOG.assertTrue(maximizedProportion != null)
      val maximizedWindow = maximizedProportion!!.first
      assert(maximizedWindow === toolWindow)
      resizerAndComponent.first.setSize(maximizedProportion.second)
      state.maximizedProportion = null
    }
    doLayout()
  }

  internal fun reset() {
    buttonManager.reset()
    state = ToolWindowPaneState()
    revalidate()
  }

  internal fun interface Resizer {
    fun setSize(size: Int)

    abstract class Splitter internal constructor(var splitter: ThreeComponentsSplitter) : Resizer {
      internal class FirstComponent(splitter: ThreeComponentsSplitter) : Splitter(splitter) {
        override fun setSize(size: Int) {
          splitter.firstSize = size
        }
      }

      internal class LastComponent(splitter: ThreeComponentsSplitter) : Splitter(splitter) {
        override fun setSize(size: Int) {
          splitter.lastSize = size
        }
      }
    }

    @Suppress("FunctionName")
    abstract class LayeredPane internal constructor(var component: Component) : Resizer {
      override fun setSize(size: Int) {
        _setSize(size)
        if (component.parent is JComponent) {
          val parent = component as JComponent
          parent.revalidate()
          parent.repaint()
        }
      }

      abstract fun _setSize(size: Int)

      internal class Left(component: Component) : LayeredPane(component) {
        override fun _setSize(size: Int) {
          component.setSize(size, component.height)
        }
      }

      internal class Right(component: Component) : LayeredPane(component) {
        override fun _setSize(size: Int) {
          val bounds = component.bounds
          val delta = size - bounds.width
          bounds.x -= delta
          bounds.width += delta
          component.bounds = bounds
        }
      }

      internal class Top(component: Component) : LayeredPane(component) {
        override fun _setSize(size: Int) {
          component.setSize(component.width, size)
        }
      }

      internal class Bottom(component: Component) : LayeredPane(component) {
        override fun _setSize(size: Int) {
          val bounds = component.bounds
          val delta = size - bounds.height
          bounds.y -= delta
          bounds.height += delta
          component.bounds = bounds
        }
      }
    }
  }

  private fun addAndSplitDockedComponentCmd(newComponent: JComponent,
                                            info: WindowInfo,
                                            dirtyMode: Boolean,
                                            manager: ToolWindowManagerImpl) {
    val anchor = info.anchor

    class MySplitter : OnePixelSplitter(), UISettingsListener {
      override fun uiSettingsChanged(uiSettings: UISettings) {
        if (anchor == ToolWindowAnchor.LEFT) {
          orientation = !uiSettings.leftHorizontalSplit
        }
        else if (anchor == ToolWindowAnchor.RIGHT) {
          orientation = !uiSettings.rightHorizontalSplit
        }
      }

      override fun toString() = "[$firstComponent|$secondComponent]"
    }

    val splitter = MySplitter()
    splitter.orientation = anchor.isSplitVertically
    if (!anchor.isHorizontal) {
      splitter.setAllowSwitchOrientationByMouseClick(true)
      splitter.addPropertyChangeListener { event ->
        if (Splitter.PROP_ORIENTATION != event.propertyName) {
          return@addPropertyChangeListener
        }
        val isSplitterHorizontalNow = !splitter.isVertical
        val settings = UISettings.getInstance()
        if (anchor == ToolWindowAnchor.LEFT) {
          if (settings.leftHorizontalSplit != isSplitterHorizontalNow) {
            settings.leftHorizontalSplit = isSplitterHorizontalNow
            settings.fireUISettingsChanged()
          }
        }
        if (anchor == ToolWindowAnchor.RIGHT) {
          if (settings.rightHorizontalSplit != isSplitterHorizontalNow) {
            settings.rightHorizontalSplit = isSplitterHorizontalNow
            settings.fireUISettingsChanged()
          }
        }
      }
    }

    var c = getComponentAt(anchor)
    // if all components are hidden for anchor, we should find the second component to put in a splitter
    // otherwise we add empty splitter
    if (c == null) {
      val toolWindows = manager.getToolWindowsOn(paneId, anchor, info.id!!)
      toolWindows.removeIf { window -> window.isSplitMode == info.isSplit || !window.isVisible }
      if (!toolWindows.isEmpty()) {
        c = (toolWindows[0] as ToolWindowImpl).decoratorComponent
      }
      if (c == null) {
        LOG.error("Empty splitter @ $anchor during AddAndSplitDockedComponentCmd for ${info.id}")
      }
    }

    val newWeight: Float
    if (c is InternalDecoratorImpl) {
      val oldComponent = c
      val oldInfo = getRegisteredMutableInfoOrLogError(oldComponent)
      if (isLookAndFeelUpdated) {
        IJSwingUtilities.updateComponentTreeUI(oldComponent)
        IJSwingUtilities.updateComponentTreeUI(newComponent)
        isLookAndFeelUpdated = false
      }
      if (info.isSplit) {
        splitter.firstComponent = oldComponent
        splitter.secondComponent = newComponent
        LOG.debug { "Determining the split proportion for ${oldInfo.id}+${info.id} " +
                    "using oldInfo.sideWeight=${oldInfo.sideWeight}" }
        val proportion = state.getPreferredSplitProportion(
          id = oldInfo.id!!,
          defaultValue = normalizeWeight(oldInfo.sideWeight / (oldInfo.sideWeight + info.sideWeight)),
        )
        splitter.proportion = proportion
        newWeight = if (!anchor.isHorizontal && !anchor.isSplitVertically) {
          normalizeWeight(oldInfo.weight + info.weight)
        }
        else {
          normalizeWeight(oldInfo.weight)
        }
      }
      else {
        splitter.firstComponent = newComponent
        splitter.secondComponent = oldComponent
        splitter.proportion = normalizeWeight(info.sideWeight)
        newWeight = if (!anchor.isHorizontal && !anchor.isSplitVertically) {
          normalizeWeight(oldInfo.weight + info.weight)
        }
        else {
          normalizeWeight(info.weight)
        }
      }
      LOG.debug { "Calculated splitter weight of $newWeight for ${oldInfo.id}+${info.id} " +
                  "using isSplit=${info.isSplit}, isHorizontal=${anchor.isHorizontal}, isSplitVertically=${anchor.isSplitVertically}, " +
                  "oldInfo.weight=${oldInfo.weight}, newInfo.weight=${info.weight}" }
    }
    else {
      newWeight = normalizeWeight(info.weight)
    }
    setComponent(component = splitter, anchor = anchor, weight = newWeight)
    if (!dirtyMode) {
      layeredPane.validate()
      layeredPane.repaint()
    }
  }

  private fun addSlidingComponent(component: JComponent, info: WindowInfo, dirtyMode: Boolean) {
    if (dirtyMode || !UISettings.getInstance().animateWindows || RemoteDesktopService.isRemoteSession()) {
      // not animated
      layeredPane.add(component, PALETTE_LAYER, -1)
      layeredPane.setBoundsInPaletteLayer(component, info.anchor, info.weight)
    }
    else {
      // Prepare top image. This image is scrolling over the bottom image.
      val topImage = layeredPane.topImage
      val bounds = component.bounds
      UIUtil.useSafely(topImage.graphics) { topGraphics ->
        component.putClientProperty(TEMPORARY_ADDED, true)
        try {
          layeredPane.add(component, PALETTE_LAYER, -1)
          layeredPane.moveToFront(component)
          layeredPane.setBoundsInPaletteLayer(component, info.anchor, info.weight)
          component.paint(topGraphics)
          layeredPane.remove(component)
        }
        finally {
          component.putClientProperty(TEMPORARY_ADDED, null)
        }
      }

      // prepare bottom image
      val bottomImage = layeredPane.bottomImage
      val bottomImageOffset = PaintUtil.getFractOffsetInRootPane(layeredPane)
      UIUtil.useSafely(bottomImage.graphics) { bottomGraphics ->
        bottomGraphics.setClip(0, 0, bounds.width, bounds.height)
        bottomGraphics.translate(bottomImageOffset.x - bounds.x, bottomImageOffset.y - bounds.y)
        layeredPane.paint(bottomGraphics)
      }

      // start animation
      val surface = Surface(myTopImage = topImage,
                            myBottomImage = bottomImage,
                            bottomImageOffset = PaintUtil.negate(bottomImageOffset),
                            direction = 1,
                            anchor = info.anchor,
                            desiredTimeToComplete = UISettings.ANIMATION_DURATION)
      layeredPane.add(surface, PALETTE_LAYER, -1)
      surface.bounds = bounds
      layeredPane.validate()
      layeredPane.repaint()
      surface.runMovement()
      layeredPane.remove(surface)
      layeredPane.add(component, PALETTE_LAYER, -1)
    }
    if (!dirtyMode) {
      layeredPane.validate()
      layeredPane.repaint()
    }
  }

  private fun removeSlidingComponent(component: Component, info: WindowInfo, dirtyMode: Boolean) {
    if (!dirtyMode && UISettings.getInstance().animateWindows && !RemoteDesktopService.isRemoteSession()) {
      val bounds = component.bounds
      // Prepare top image. This image is scrolling over the bottom image. It contains a picture of component is being removed.
      val topImage: Image = layeredPane.topImage
      UIUtil.useSafely(topImage.graphics) { g: Graphics2D? -> component.paint(g) }

      // Prepare the bottom image.
      // This image contains a picture of a component that is located
      // under the component to is being removed.
      val bottomImage: Image = layeredPane.bottomImage
      val bottomImageOffset = PaintUtil.getFractOffsetInRootPane(layeredPane)
      UIUtil.useSafely(bottomImage.graphics) { bottomGraphics: Graphics2D ->
        layeredPane.remove(component)
        bottomGraphics.clipRect(0, 0, bounds.width, bounds.height)
        bottomGraphics.translate(bottomImageOffset.x - bounds.x, bottomImageOffset.y - bounds.y)
        layeredPane.paint(bottomGraphics)
      }

      // Remove component from the layered pane and start animation.
      val surface = Surface(topImage, bottomImage, PaintUtil.negate(bottomImageOffset), -1, info.anchor, UISettings.ANIMATION_DURATION)
      layeredPane.add(surface, PALETTE_LAYER, -1)
      surface.bounds = bounds
      layeredPane.validate()
      layeredPane.repaint()
      surface.runMovement()
      layeredPane.remove(surface)
    }
    else {
      // not animated
      layeredPane.remove(component)
    }
    if (!dirtyMode) {
      layeredPane.validate()
      layeredPane.repaint()
    }
  }

  internal fun setStripesOverlaid(value: Boolean) {
    state.isStripesOverlaid = value
    updateToolStripesVisibility(UISettings.getInstance())
  }
}

private class ImageRef(image: BufferedImage) : SoftReference<BufferedImage?>(image) {
  private var strongRef: BufferedImage?

  init {
    strongRef = image
  }

  override fun get(): BufferedImage? {
    val img = strongRef ?: super.get()
    // drop on first request
    strongRef = null
    return img
  }
}

private class ImageCache(imageProvider: (ScaleContext) -> ImageRef) : ScaleContextCache<ImageRef>(imageProvider) {
  fun get(scaleContext: ScaleContext): BufferedImage {
    val ref = getOrProvide(scaleContext)
    ref?.get()?.let {
      return it
    }
    // clear to recalculate the image
    clear()
    // the first recalculated image will be non-null
    return get(scaleContext)
  }
}

private class FrameLayeredPane(splitter: JComponent, frame: JFrame) : JLayeredPane() {
  private val imageProvider: (ScaleContext) -> ImageRef = {
    val width = max(max(1, width), frame.width)
    val height = max(max(1, height), frame.height)
    ImageRef(ImageUtil.createImage(graphicsConfiguration, width, height, BufferedImage.TYPE_INT_RGB))
  }

  /*
   * These images are used to perform animated showing and hiding of components.
   * They are the member for performance reason.
   */
  private val bottomImageCache = ImageCache(imageProvider)
  private val topImageCache = ImageCache(imageProvider)

  init {
    isOpaque = false
    add(splitter, DEFAULT_LAYER, -1)
  }

  val bottomImage: Image
    get() = bottomImageCache.get(ScaleContext.create(this))
  val topImage: Image
    get() = topImageCache.get(ScaleContext.create(this))

  /**
   * When component size becomes larger than bottom and top images should be enlarged.
   */
  override fun doLayout() {
    val width = width
    val height = height
    if (width < 0 || height < 0) {
      return
    }

    // Resize component at the DEFAULT layer. It should be only on component in that layer
    var components = getComponentsInLayer(DEFAULT_LAYER)
    LOG.assertTrue(components.size <= 1)
    for (component in components) {
      component.setBounds(0, 0, getWidth(), getHeight())
    }
    // Resize components at the PALETTE layer
    components = getComponentsInLayer(PALETTE_LAYER)
    for (component in components) {
      if (component !is InternalDecoratorImpl) {
        continue
      }

      val info = component.toolWindow.windowInfo
      val rootWidth = rootPane?.width ?: 0
      val rootHeight = rootPane?.height ?: 0
      val weight = if (info.anchor.isHorizontal) getAdjustedRatio(component.getHeight(), rootHeight, 1)
      else getAdjustedRatio(component.getWidth(), rootWidth, 1)
      setBoundsInPaletteLayer(component, info.anchor, weight)
    }
  }

  fun setBoundsInPaletteLayer(component: Component, anchor: ToolWindowAnchor, w: Float) {
    var weight = w
    if (weight < .0f) {
      weight = WindowInfoImpl.DEFAULT_WEIGHT
    }
    else if (weight > 1.0f) {
      weight = 1.0f
    }
    val rootHeight = rootPane?.height ?: 0
    val rootWidth = rootPane?.width ?: 0
    when (anchor) {
      ToolWindowAnchor.TOP -> {
        component.setBounds(0, 0, width, (rootHeight * weight).toInt())
      }
      ToolWindowAnchor.LEFT -> {
        component.setBounds(0, 0, (rootWidth * weight).toInt(), height)
      }
      ToolWindowAnchor.BOTTOM -> {
        val height = (rootHeight * weight).toInt()
        component.setBounds(0, getHeight() - height, width, height)
      }
      ToolWindowAnchor.RIGHT -> {
        val width = (rootWidth * weight).toInt()
        component.setBounds(getWidth() - width, 0, width, height)
      }
      else -> {
        LOG.error("unknown anchor $anchor")
      }
    }
  }
}

private class Surface(private val myTopImage: Image,
                      private val myBottomImage: Image,
                      bottomImageOffset: Point2D,
                      private val  direction: Int,
                      private val anchor: ToolWindowAnchor,
                      private val desiredTimeToComplete: Int) : JComponent() {
  private val bottomImageOffset = bottomImageOffset.clone() as Point2D
  private var offset = 0

  init {
    isOpaque = true
  }

  fun runMovement() {
    if (!isShowing) {
      return
    }

    val bounds = bounds
    val distance = if (anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT) bounds.width else bounds.height
    var count = 0
    offset = 0
    // first paint requires more time than next ones
    paintImmediately(0, 0, width, height)
    val startTime = System.currentTimeMillis()
    while (true) {
      paintImmediately(0, 0, width, height)
      val timeSpent = System.currentTimeMillis() - startTime
      count++
      if (timeSpent >= desiredTimeToComplete) {
        break
      }
      val onePaintTime = timeSpent.toDouble() / count
      var iterations = ((desiredTimeToComplete - timeSpent) / onePaintTime).toInt()
      iterations = 1.coerceAtLeast(iterations)
      offset += (distance - offset) / iterations
    }
  }

  override fun paint(g: Graphics) {
    val bounds = bounds
    (g as Graphics2D).translate(bottomImageOffset.x, bottomImageOffset.y)
    when (anchor) {
      ToolWindowAnchor.LEFT -> {
        if (direction == 1) {
          g.setClip(null)
          g.clipRect(offset, 0, bounds.width - offset, bounds.height)
          UIUtil.drawImage(g, myBottomImage, 0, 0, null)
          g.setClip(null)
          g.clipRect(0, 0, offset, bounds.height)
          UIUtil.drawImage(g, myTopImage, offset - bounds.width, 0, null)
        }
        else {
          g.setClip(null)
          g.clipRect(bounds.width - offset, 0, offset, bounds.height)
          UIUtil.drawImage(g, myBottomImage, 0, 0, null)
          g.setClip(null)
          g.clipRect(0, 0, bounds.width - offset, bounds.height)
          UIUtil.drawImage(g, myTopImage, -offset, 0, null)
        }
        myTopImage.flush()
      }
      ToolWindowAnchor.RIGHT -> {
        if (direction == 1) {
          g.setClip(null)
          g.clipRect(0, 0, bounds.width - offset, bounds.height)
          UIUtil.drawImage(g, myBottomImage, 0, 0, null)
          g.setClip(null)
          g.clipRect(bounds.width - offset, 0, offset, bounds.height)
          UIUtil.drawImage(g, myTopImage, bounds.width - offset, 0, null)
        }
        else {
          g.setClip(null)
          g.clipRect(0, 0, offset, bounds.height)
          UIUtil.drawImage(g, myBottomImage, 0, 0, null)
          g.setClip(null)
          g.clipRect(offset, 0, bounds.width - offset, bounds.height)
          UIUtil.drawImage(g, myTopImage, offset, 0, null)
        }
      }
      ToolWindowAnchor.TOP -> {
        if (direction == 1) {
          g.setClip(null)
          g.clipRect(0, offset, bounds.width, bounds.height - offset)
          UIUtil.drawImage(g, myBottomImage, 0, 0, null)
          g.setClip(null)
          g.clipRect(0, 0, bounds.width, offset)
          UIUtil.drawImage(g, myTopImage, 0, -bounds.height + offset, null)
        }
        else {
          g.setClip(null)
          g.clipRect(0, bounds.height - offset, bounds.width, offset)
          UIUtil.drawImage(g, myBottomImage, 0, 0, null)
          g.setClip(null)
          g.clipRect(0, 0, bounds.width, bounds.height - offset)
          UIUtil.drawImage(g, myTopImage, 0, -offset, null)
        }
      }
      ToolWindowAnchor.BOTTOM -> {
        if (direction == 1) {
          g.setClip(null)
          g.clipRect(0, 0, bounds.width, bounds.height - offset)
          UIUtil.drawImage(g, myBottomImage, 0, 0, null)
          g.setClip(null)
          g.clipRect(0, bounds.height - offset, bounds.width, offset)
          UIUtil.drawImage(g, myTopImage, 0, bounds.height - offset, null)
        }
        else {
          g.setClip(null)
          g.clipRect(0, 0, bounds.width, offset)
          UIUtil.drawImage(g, myBottomImage, 0, 0, null)
          g.setClip(null)
          g.clipRect(0, offset, bounds.width, bounds.height - offset)
          UIUtil.drawImage(g, myTopImage, 0, offset, null)
        }
      }
    }
  }
}