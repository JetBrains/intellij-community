// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.RemoteDesktopService
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl.Companion.getAdjustedRatio
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl.Companion.getRegisteredMutableInfoOrLogError
import com.intellij.openapi.wm.impl.ToolWindowsPane.Resizer.LayeredPane
import com.intellij.openapi.wm.impl.ToolWindowsPane.Resizer.Splitter.FirstComponent
import com.intellij.openapi.wm.impl.ToolWindowsPane.Resizer.Splitter.LastComponent
import com.intellij.reference.SoftReference
import com.intellij.toolWindow.*
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.image.BufferedImage
import java.beans.PropertyChangeEvent
import java.util.*
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.LayoutFocusTraversalPolicy
import kotlin.math.max
import kotlin.math.min

/**
 * This panel contains all tool stripes and JLayeredPane at the center area. All tool windows are
 * located inside this layered pane.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
class ToolWindowsPane internal constructor(frame: JFrame,
                                           parentDisposable: Disposable,
                                           @field:JvmField internal val buttonManager: ToolWindowButtonManager) : JBLayeredPane(), UISettingsListener {
  companion object {
    private val LOG = Logger.getInstance(ToolWindowsPane::class.java)
    const val TEMPORARY_ADDED = "TEMPORARY_ADDED"

    //The size of topmost 'resize' area when toolwindow caption is used for both resize and drag
    val headerResizeArea: Int
      get() = JBUI.scale(Registry.intValue("ide.new.tool.window.resize.area.height", 14, 1, 26))

    private fun normalizeWeight(weight: Float): Float {
      if (weight <= 0) return WindowInfoImpl.DEFAULT_WEIGHT
      return if (weight >= 1) 1 - WindowInfoImpl.DEFAULT_WEIGHT else weight
    }
  }

  private var isLookAndFeelUpdated = false
  private val frame: JFrame
  private var state = ToolWindowPaneState()

  /**
   * This panel is the layered pane where all sliding tool windows are located. The DEFAULT
   * layer contains splitters. The PALETTE layer contains all sliding tool windows.
   */
  private val layeredPane: MyLayeredPane

  /*
   * Splitters.
   */
  private val verticalSplitter: ThreeComponentsSplitter
  private val horizontalSplitter: ThreeComponentsSplitter
  private var isWideScreen: Boolean
  private var leftHorizontalSplit: Boolean
  private var rightHorizontalSplit: Boolean

  init {
    isOpaque = false
    this.frame = frame

    // splitters
    verticalSplitter = ThreeComponentsSplitter(true, parentDisposable)
    val registryValue = Registry.get("ide.mainSplitter.min.size")
    registryValue.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        updateInnerMinSize(value)
      }
    }, parentDisposable)
    verticalSplitter.dividerWidth = 0
    verticalSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"))
    verticalSplitter.background = Color.gray
    horizontalSplitter = ThreeComponentsSplitter(false, parentDisposable)
    horizontalSplitter.dividerWidth = 0
    horizontalSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"))
    horizontalSplitter.background = Color.gray
    updateInnerMinSize(registryValue)
    val uiSettings = instance
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
    layeredPane = MyLayeredPane(if (isWideScreen) horizontalSplitter else verticalSplitter)

    // compose layout
    buttonManager.addToToolWindowPane(this)
    add(layeredPane, DEFAULT_LAYER)
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    if (Registry.`is`("ide.new.tool.window.dnd")) {
      ToolWindowDragHelper(parentDisposable, this).start()
    }
    if (Registry.`is`("ide.allow.split.and.reorder.in.tool.window")) {
      ToolWindowInnerDragHelper(parentDisposable, this).start()
    }
    val app = ApplicationManager.getApplication()
    app.messageBus.connect(parentDisposable).subscribe(LafManagerListener.TOPIC, LafManagerListener { isLookAndFeelUpdated = true })
  }

  private fun updateInnerMinSize(value: RegistryValue) {
    val minSize = max(0, min(100, value.asInteger()))
    verticalSplitter.setMinSize(JBUIScale.scale(minSize))
    horizontalSplitter.setMinSize(JBUIScale.scale(minSize))
  }

  override fun doLayout() {
    buttonManager.layout(size, layeredPane)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateToolStripesVisibility(uiSettings)
    updateLayout(uiSettings)
  }

  /**
   * @param dirtyMode if `true` then JRootPane will not be validated and repainted after adding
   * the decorator. Moreover, in this (dirty) mode animation doesn't work.
   */
  fun addDecorator(decorator: JComponent, info: WindowInfo, dirtyMode: Boolean, manager: ToolWindowManagerImpl) {
    if (info.isDocked) {
      val side = !info.isSplit
      val anchor = info.anchor
      val sideInfo = manager.getDockedInfoAt(anchor, side)
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
      val sideInfo = manager.getDockedInfoAt(anchor, !info.isSplit)
      if (sideInfo == null) {
        setComponent(null, anchor, 0f)
      }
      else {
        val c = getComponentAt(anchor)
        if (c is Splitter) {
          val component1 = (if (info.isSplit) c.firstComponent else c.secondComponent) as InternalDecoratorImpl
          state.addSplitProportion(info, component1, c)
          setComponent(component1, anchor, getRegisteredMutableInfoOrLogError(component1).weight)
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

  fun revalidateNotEmptyStripes() {
    buttonManager.revalidateNotEmptyStripes()
  }

  private fun setComponent(component: JComponent?, anchor: ToolWindowAnchor, weight: Float) {
    val size = rootPane.size
    when (anchor) {
      ToolWindowAnchor.TOP -> {
        verticalSplitter.firstComponent = component
        verticalSplitter.firstSize = (size.getHeight() * weight).toInt()
      }
      ToolWindowAnchor.LEFT -> {
        horizontalSplitter.firstComponent = component
        horizontalSplitter.firstSize = (size.getWidth() * weight).toInt()
      }
      ToolWindowAnchor.BOTTOM -> {
        verticalSplitter.lastComponent = component
        verticalSplitter.lastSize = (size.getHeight() * weight).toInt()
      }
      ToolWindowAnchor.RIGHT -> {
        horizontalSplitter.lastComponent = component
        horizontalSplitter.lastSize = (size.getWidth() * weight).toInt()
      }
      else -> {
        LOG.error("unknown anchor: $anchor")
      }
    }
  }

  private fun getComponentAt(anchor: ToolWindowAnchor): JComponent? {
    return when(anchor) {
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

  fun setDocumentComponent(component: JComponent?) {
    (if (isWideScreen) verticalSplitter else horizontalSplitter).innerComponent = component
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

  internal fun getStripeFor(screenPoint: Point, preferred: AbstractDroppableStripe): AbstractDroppableStripe? {
    return buttonManager.getStripeFor(screenPoint, preferred, this)
  }

  fun stretchWidth(window: ToolWindow, value: Int) {
    stretch(window, value)
  }

  fun stretchHeight(window: ToolWindow, value: Int) {
    stretch(window, value)
  }

  private fun stretch(wnd: ToolWindow, value: Int) {
    val pair = findResizerAndComponent(wnd) ?: return
    val vertical = wnd.anchor == ToolWindowAnchor.TOP || wnd.anchor == ToolWindowAnchor.BOTTOM
    val actualSize = (if (vertical) pair.second.height else pair.second.width) + value
    val first = wnd.anchor == ToolWindowAnchor.LEFT || wnd.anchor == ToolWindowAnchor.TOP
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
          if (verticalSplitter.firstComponent === component) FirstComponent(verticalSplitter) else LastComponent(verticalSplitter)
        }
        else {
          if (horizontalSplitter.firstComponent === component) FirstComponent(horizontalSplitter) else LastComponent(horizontalSplitter)
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
          ToolWindowAnchor.TOP -> LayeredPane.Top(component)
          ToolWindowAnchor.BOTTOM -> LayeredPane.Bottom(component)
          ToolWindowAnchor.LEFT -> LayeredPane.Left(component)
          ToolWindowAnchor.RIGHT -> LayeredPane.Right(component)
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
      layeredPane.add(if (isWideScreen) horizontalSplitter else verticalSplitter, DEFAULT_LAYER)
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

  fun isMaximized(window: ToolWindow) = state.isMaximized(window)

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

  fun reset() {
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

    val splitter: Splitter = MySplitter()
    splitter.orientation = anchor.isSplitVertically
    if (!anchor.isHorizontal) {
      splitter.setAllowSwitchOrientationByMouseClick(true)
      splitter.addPropertyChangeListener { evt: PropertyChangeEvent ->
        if (Splitter.PROP_ORIENTATION != evt.propertyName) return@addPropertyChangeListener
        val isSplitterHorizontalNow = !splitter.isVertical
        val settings = instance
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
    // if all components are hidden for anchor we should find the second component to put in a splitter
    // otherwise we add empty splitter
    if (c == null) {
      val toolWindows = manager.getToolWindowsOn(anchor, info.id!!)
      toolWindows.removeIf { window: ToolWindowEx? -> window == null || window.isSplitMode == info.isSplit || !window.isVisible }
      if (!toolWindows.isEmpty()) {
        c = (toolWindows[0] as ToolWindowImpl).decoratorComponent
      }
      if (c == null) {
        LOG.error("Empty splitter @ " + anchor + " during AddAndSplitDockedComponentCmd for " + info.id)
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
        val proportion = state.getPreferredSplitProportion(Objects.requireNonNull(oldInfo.id), normalizeWeight(
          oldInfo.sideWeight / (oldInfo.sideWeight + info.sideWeight)))
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
    }
    else {
      newWeight = normalizeWeight(info.weight)
    }
    setComponent(splitter, anchor, newWeight)
    if (!dirtyMode) {
      layeredPane.validate()
      layeredPane.repaint()
    }
  }

  private fun addSlidingComponent(component: JComponent, info: WindowInfo, dirtyMode: Boolean) {
    if (dirtyMode || !instance.animateWindows || RemoteDesktopService.isRemoteSession()) {
      // not animated
      layeredPane.add(component, PALETTE_LAYER)
      layeredPane.setBoundsInPaletteLayer(component, info.anchor, info.weight)
    }
    else {
      // Prepare top image. This image is scrolling over bottom image.
      val topImage: Image = layeredPane.topImage
      val bounds = component.bounds
      UIUtil.useSafely(topImage.graphics) { topGraphics ->
        component.putClientProperty(TEMPORARY_ADDED, java.lang.Boolean.TRUE)
        try {
          layeredPane.add(component, PALETTE_LAYER)
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
      val bottomImage: Image = layeredPane.bottomImage
      val bottomImageOffset = PaintUtil.getFractOffsetInRootPane(layeredPane)
      UIUtil.useSafely(bottomImage.graphics) { bottomGraphics: Graphics2D ->
        bottomGraphics.setClip(0, 0, bounds.width, bounds.height)
        bottomGraphics.translate(bottomImageOffset.x - bounds.x, bottomImageOffset.y - bounds.y)
        layeredPane.paint(bottomGraphics)
      }

      // start animation.
      val surface = Surface(topImage, bottomImage, PaintUtil.negate(bottomImageOffset), 1, info.anchor, UISettings.ANIMATION_DURATION)
      layeredPane.add(surface, PALETTE_LAYER)
      surface.bounds = bounds
      layeredPane.validate()
      layeredPane.repaint()
      surface.runMovement()
      layeredPane.remove(surface)
      layeredPane.add(component, PALETTE_LAYER)
    }
    if (!dirtyMode) {
      layeredPane.validate()
      layeredPane.repaint()
    }
  }

  private fun removeSlidingComponent(component: Component, info: WindowInfo, dirtyMode: Boolean) {
    val uiSettings = instance
    if (!dirtyMode && uiSettings.animateWindows && !RemoteDesktopService.isRemoteSession()) {
      val bounds = component.bounds
      // Prepare top image. This image is scrolling over bottom image. It contains
      // picture of component is being removed.
      val topImage: Image = layeredPane.topImage
      UIUtil.useSafely(topImage.graphics) { g: Graphics2D? -> component.paint(g) }

      // Prepare bottom image. This image contains picture of component that is located
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
      layeredPane.add(surface, PALETTE_LAYER)
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

  private class ImageCache(imageProvider: Function<in ScaleContext, ImageRef>) : ScaleContext.Cache<ImageRef?>(imageProvider) {
    fun get(ctx: ScaleContext): BufferedImage {
      val ref = getOrProvide(ctx)
      val image = SoftReference.dereference(ref)
      if (image != null) return image
      clear() // clear to recalculate the image
      return get(ctx) // first recalculated image will be non-null
    }
  }

  private inner class MyLayeredPane(splitter: JComponent) : JBLayeredPane() {
    private val imageProvider: Function<ScaleContext, ImageRef> = Function {
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
      add(splitter, DEFAULT_LAYER)
    }

    val bottomImage: Image
      get() = bottomImageCache.get(ScaleContext.create(this))
    val topImage: Image
      get() = topImageCache.get(ScaleContext.create(this))

    /**
     * When component size becomes larger then bottom and top images should be enlarged.
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
        val rootWidth = rootPane.width
        val rootHeight = rootPane.height
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
      val rootHeight = rootPane.height
      val rootWidth = rootPane.width
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

  fun setStripesOverlayed(value: Boolean) {
    state.isStripesOverlaid = value
    updateToolStripesVisibility(instance)
  }
 }