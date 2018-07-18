/*
 * License (BSD):
 * ==============
 *
 * Copyright (c) 2004, Mikael Grev, MiG InfoCom AB. (miglayout (at) miginfocom (dot) com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * Neither the name of the MiG InfoCom AB nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 * @version 1.0
 * @author Mikael Grev, MiG InfoCom AB
 *         Date: 2006-sep-08
 */
package com.intellij.ui.layout.migLayout.patched

import gnu.trove.THashMap
import net.miginfocom.layout.*
import java.awt.*
import java.awt.event.ActionListener
import javax.swing.*

/** A very flexible layout manager.
 * Read the documentation that came with this layout manager for information on usage.
 */
open class MigLayout @JvmOverloads constructor(val layoutConstraints: LC = LC(), val columnConstraints: AC = AC(), val rowConstraints: AC = AC()) : LayoutManager2 {
  @Transient
  private var cacheParentW: ContainerWrapper? = null

  @Transient
  private val componentWrapperToConstraints = THashMap<ComponentWrapper, CC>()
  @Transient
  private var debugTimer: Timer? = null

  @Transient
  private var grid: Grid? = null
  @Transient
  private var lastModCount = PlatformDefaults.getModCount()
  @Transient
  private var lastHash = -1
  @Transient
  private var lastInvalidSize: Dimension? = null
  @Transient
  private var lastWasInvalid = false
  @Transient
  private var lastParentSize: Dimension? = null

  @Transient
  private var dirty = true

  var isDebugEnabled: Boolean = false

  private val debugMillis: Int
    get() {
      val globalDebugMillis = LayoutUtil.getGlobalDebugMillis()
      return if (globalDebugMillis > 0) globalDebugMillis else layoutConstraints.debugMillis
    }

  private var lastSize: Long = 0

  private fun stopDebug() {
    if (debugTimer != null) {
      debugTimer!!.stop()
      debugTimer = null
    }
  }

  /** Sets the debugging state for this layout manager instance. If debug is turned on a timer will repaint the last laid out parent
   * with debug information on top.
   *
   * Red fill and dashed red outline is used to indicate occupied cells in the grid. Blue dashed outline indicate
   * component bounds set.
   *
   * Note that debug can also be set on the layout constraints. There it will be persisted. The value set here will not. See the class
   * JavaDocs for information.
   * @param parentWrapper The parent to set debug for.
   */
  private fun startDebug(parentWrapper: ComponentWrapper?) {
    if (debugTimer?.delay == debugMillis) {
      return
    }

    debugTimer?.stop()

    debugTimer = Timer(debugMillis, ActionListener {
      val grid = grid
      if (grid != null && (grid.container.component as Component).isShowing) {
        grid.paintDebug()
      }
      else {
        debugTimer!!.stop()
        debugTimer = null
      }
    })

    val parent = parentWrapper?.parent?.component as? Component
    if (parent != null) {
      SwingUtilities.invokeLater {
        val p = parent.parent ?: return@invokeLater
        if (p is JComponent) {
          p.revalidate()
        }
        else {
          parent.invalidate()
          p.validate()
        }
      }
    }

    debugTimer!!.initialDelay = 100
    debugTimer!!.start()
  }

  /** Check if something has changed and if so recreate it to the cached objects.
   * @param parent The parent that is the target for this layout manager.
   */
  private fun checkCache(parent: Container) {
    if (dirty) {
      grid = null
    }

    componentWrapperToConstraints.retainEntries { wrapper, _ -> (wrapper as SwingComponentWrapper).component.parent === parent }

    // check if the grid is valid
    val mc = PlatformDefaults.getModCount()
    if (lastModCount != mc) {
      grid = null
      lastModCount = mc
    }

    if (parent.isValid) {
      lastWasInvalid = false
    }
    else if (!lastWasInvalid) {
      lastWasInvalid = true

      var hash = 0
      var resetLastInvalidOnParent = false // Added in 3.7.3 to resolve a timing regression introduced in 3.7.1
      for (wrapper in componentWrapperToConstraints.keys) {
        val component = wrapper.component
        if (component is JTextArea || component is JEditorPane) {
          resetLastInvalidOnParent = true
        }

        hash = hash xor wrapper.layoutHashCode
        hash += 285134905
      }
      if (resetLastInvalidOnParent) {
        resetLastInvalidOnParent(parent)
      }

      if (hash != lastHash) {
        grid = null
        lastHash = hash
      }

      val ps = parent.size
      if (lastInvalidSize == null || lastInvalidSize != ps) {
        grid = null
        lastInvalidSize = ps
      }
    }

    val par = checkParent(parent)

    if (debugMillis > 0) {
      startDebug(par)
    }
    else {
      stopDebug()
    }

    if (grid == null) {
      grid = Grid(par!!, layoutConstraints, rowConstraints, columnConstraints, componentWrapperToConstraints, null)
    }

    dirty = false
  }

  /**
   * @since 3.7.3
   */
  private fun resetLastInvalidOnParent(parent: Container?) {
    @Suppress("NAME_SHADOWING")
    var parent = parent
    while (parent != null) {
      val layoutManager = parent.layout
      if (layoutManager is MigLayout) {
        layoutManager.lastWasInvalid = false
      }
      parent = parent.parent
    }
  }

  private fun checkParent(parent: Container?): ContainerWrapper? {
    if (parent == null) {
      return null
    }

    if (cacheParentW == null || cacheParentW!!.component !== parent) {
      cacheParentW = SwingContainerWrapper((parent as JComponent?)!!)
    }

    return cacheParentW
  }

  override fun layoutContainer(parent: Container) {
    synchronized(parent.treeLock) {
      checkCache(parent)

      val insets = parent.insets
      val bounds = intArrayOf(insets.left, insets.top, parent.width - insets.left - insets.right, parent.height - insets.top - insets.bottom)
      val isDebugEnabled = isDebugEnabled || debugMillis > 0
      if (grid!!.layout(bounds, layoutConstraints.alignX, layoutConstraints.alignY, isDebugEnabled)) {
        grid = null
        checkCache(parent)
        grid!!.layout(bounds, layoutConstraints.alignX, layoutConstraints.alignY, isDebugEnabled)
      }

      val newSize = grid!!.height[1] + (grid!!.width[1].toLong() shl 32)
      if (lastSize != newSize) {
        lastSize = newSize
        val containerWrapper = checkParent(parent)
        val win = SwingUtilities.getAncestorOfClass(Window::class.java, containerWrapper!!.component as Component) as? Window
        if (win != null) {
          if (win.isVisible) {
            SwingUtilities.invokeLater { adjustWindowSize(containerWrapper) }
          }
          else {
            adjustWindowSize(containerWrapper)
          }
        }
      }
      lastInvalidSize = null
    }
  }

  /** Checks the parent window/popup if its size is within parameters as set by the LC.
   * @param parent The parent who's window to possibly adjust the size for.
   */
  private fun adjustWindowSize(parent: ContainerWrapper?) {
    val wBounds = layoutConstraints.packWidth
    val hBounds = layoutConstraints.packHeight
    if (wBounds === BoundSize.NULL_SIZE && hBounds === BoundSize.NULL_SIZE) {
      return
    }

    val packable = getPackable(parent!!.component as Component) ?: return

    val pc = parent.component as Component

    var c = pc as? Container ?: pc.parent
    while (c != null) {
      val layout = c.layout
      if (layout is BoxLayout || layout is OverlayLayout) {
        (layout as LayoutManager2).invalidateLayout(c)
      }
      c = c.parent
    }

    val prefSize = packable.preferredSize
    val targetW = constrain(checkParent(packable), packable.width, prefSize.width, wBounds)
    val targetH = constrain(checkParent(packable), packable.height, prefSize.height, hBounds)

    val p = if (packable.isShowing) packable.locationOnScreen else packable.location

    val x = Math.round(p.x - (targetW - packable.width) * (1 - layoutConstraints.packWidthAlign))
    val y = Math.round(p.y - (targetH - packable.height) * (1 - layoutConstraints.packHeightAlign))

    if (packable is JPopupMenu) {
      val popupMenu = packable as JPopupMenu?
      popupMenu!!.isVisible = false
      popupMenu.setPopupSize(targetW, targetH)
      val invoker = popupMenu.invoker
      val popPoint = Point(x, y)
      SwingUtilities.convertPointFromScreen(popPoint, invoker)
      packable.show(invoker, popPoint.x, popPoint.y)

      // reset preferred size so we don't read it again.
      packable.preferredSize = null
    }
    else {
      packable.setBounds(x, y, targetW, targetH)
    }
  }

  /**
   * Returns a high level window or popup to pack, if any.
   */
  private fun getPackable(comp: Component): Container? {
    val popup = findType(JPopupMenu::class.java, comp)
    if (popup != null) {
      // Lightweight/HeavyWeight popup must be handled separately
      var popupComp: Container? = popup
      while (popupComp != null) {
        if (popupComp.javaClass.name.contains("HeavyWeightWindow")) {
          // return the heavy weight window for normal processing
          return popupComp
        }
        popupComp = popupComp.parent
      }
      return popup // Return the JPopup.
    }

    return findType(Window::class.java, comp)
  }

  private fun constrain(parent: ContainerWrapper?, winSize: Int, prefSize: Int, constrain: BoundSize?): Int {
    if (constrain == null) {
      return winSize
    }

    var retSize = winSize
    constrain.preferred?.let { wUV ->
      retSize = wUV.getPixels(prefSize.toFloat(), parent, parent)
    }

    retSize = constrain.constrain(retSize, prefSize.toFloat(), parent)

    return if (constrain.gapPush) Math.max(winSize, retSize) else retSize
  }

  fun getComponentConstraints(): Map<Component, CC> {
    val result = THashMap<Component, CC>()
    for (entry in componentWrapperToConstraints) {
      result.put((entry.key as SwingComponentWrapper).component, entry.value)
    }
    return result
  }

  override fun minimumLayoutSize(parent: Container): Dimension {
    synchronized(parent.treeLock) {
      return getSizeImpl(parent, LayoutUtil.MIN)
    }
  }

  override fun preferredLayoutSize(parent: Container): Dimension {
    synchronized(parent.treeLock) {
      if (lastParentSize == null || parent.size != lastParentSize) {
        for (wrapper in componentWrapperToConstraints.keys) {
          if (wrapper.contentBias != -1) {
            layoutContainer(parent)
            break
          }
        }
      }

      lastParentSize = parent.size
      return getSizeImpl(parent, LayoutUtil.PREF)
    }
  }

  override fun maximumLayoutSize(parent: Container): Dimension = Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

  private fun getSizeImpl(parent: Container, sizeType: Int): Dimension {
    checkCache(parent)

    val i = parent.insets

    val grid = grid
    val w = LayoutUtil.getSizeSafe(grid?.width, sizeType) + i.left + i.right
    val h = LayoutUtil.getSizeSafe(grid?.height, sizeType) + i.top + i.bottom
    return Dimension(w, h)
  }

  override fun getLayoutAlignmentX(parent: Container): Float {
    val alignX = layoutConstraints.alignX ?: return 0f
    return alignX.getPixels(1f, checkParent(parent), null).toFloat()
  }

  override fun getLayoutAlignmentY(parent: Container): Float {
    val alignY = layoutConstraints.alignY ?: return 0f
    return alignY.getPixels(1f, checkParent(parent), null).toFloat()
  }

  override fun addLayoutComponent(s: String, comp: Component) {
    addLayoutComponent(comp, s)
  }

  override fun addLayoutComponent(comp: Component, constraints: Any?) {
    synchronized(comp.parent.treeLock) {
      val componentWrapper = SwingComponentWrapper(comp as JComponent)
      if (constraints != null) {
        componentWrapperToConstraints.put(componentWrapper, constraints as CC)
      }

      dirty = true
    }
  }

  override fun removeLayoutComponent(comp: Component) {
    synchronized(comp.parent.treeLock) {
      componentWrapperToConstraints.remove(SwingComponentWrapper(comp as JComponent))
      // to clear references
      grid = null
    }
  }

  override fun invalidateLayout(target: Container) {
    dirty = true
  }
}

private fun <E> findType(clazz: Class<E>, comp: Component?): E? {
  @Suppress("NAME_SHADOWING")
  var comp = comp
  while (comp != null && !clazz.isInstance(comp)) {
    comp = comp.parent
  }
  @Suppress("UNCHECKED_CAST")
  return comp as E?
}