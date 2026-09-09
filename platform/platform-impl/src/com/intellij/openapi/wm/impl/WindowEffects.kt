@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.system.WindowsSystemLibraries
import com.intellij.util.ui.StartupUiUtil
import sun.awt.AWTAccessor
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Shape
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.lang.foreign.Arena
import java.lang.foreign.SymbolLookup
import javax.swing.RootPaneContainer

internal object WindowEffects {
  private val windows by lazy {
    WindowsWindowEffects(WindowsSystemLibraries.lookup("user32.dll").or(WindowsSystemLibraries.lookup("gdi32.dll")))
  }
  private val xlib by lazy { SymbolLookup.libraryLookup("libX11.so.6", Arena.global()) }
  private val xshape by lazy { SymbolLookup.libraryLookup("libXext.so.6", Arena.global()) }
  private val xrender by lazy { SymbolLookup.libraryLookup("libXrender.so.1", Arena.global()) }
  private val x11 by lazy {
    X11WindowEffects(SymbolLookup { name ->
      when {
        name.startsWith("XShape") -> xshape.find(name)
        name.startsWith("XRender") -> xrender.find(name)
        else -> xlib.find(name)
      }
    })
  }

  fun isAlphaSupported(): Boolean = when {
    GraphicsEnvironment.isHeadless() -> false
    SystemInfoRt.isMac -> true
    SystemInfoRt.isWindows -> java.lang.Boolean.getBoolean("sun.java2d.noddraw")
    StartupUiUtil.isXToolkit() -> x11.isAlphaSupported
    else -> false
  }

  fun setAlpha(window: Window, alpha: Float) {
    when {
      SystemInfoRt.isWindows -> whenDisplayable(window) {
        windows.setAlpha(handle(window, "getHWnd"), alpha)
        forceHeavyweightPopups(window, alpha != 1f)
      }
      StartupUiUtil.isXToolkit() -> whenDisplayable(window) { x11.setAlpha(handle(window, "getWindow"), alpha) }
    }
  }

  fun setMask(window: Window, shape: Shape?) {
    when {
      SystemInfoRt.isMac -> {
        val container = window as? RootPaneContainer
        val content = container?.contentPane ?: window.components.firstOrNull()
        val maskingPane = content as? MacWindowMask ?: MacWindowMask(content).also {
          if (container != null) container.contentPane = it else window.add(it)
        }
        maskingPane.apply(window, shape)
      }
      SystemInfoRt.isWindows || StartupUiUtil.isXToolkit() -> {
        val rectangles = WindowMask.rectangles(shape)
        whenDisplayable(window) {
          if (SystemInfoRt.isWindows) windows.setMask(handle(window, "getHWnd"), rectangles)
          else x11.setMask(handle(window, "getWindow"), rectangles)
          forceHeavyweightPopups(window, rectangles != null)
        }
      }
    }
  }

  private fun handle(window: Window, method: String): Long {
    val peer = checkNotNull(AWTAccessor.getComponentAccessor().getPeer(window))
    return peer.javaClass.getMethod(method).invoke(peer) as Long
  }

  private fun whenDisplayable(window: Window, action: () -> Unit) {
    if (window.isDisplayable) action()
    else {
      window.addHierarchyListener(object : HierarchyListener {
        override fun hierarchyChanged(event: HierarchyEvent) {
          if (event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L && window.isDisplayable) {
            window.removeHierarchyListener(this)
            try {
              action()
            }
            catch (error: Throwable) {
              logger<WindowEffects>().debug(error)
            }
          }
        }
      })
    }
  }

  private fun forceHeavyweightPopups(window: Window, force: Boolean) {
    val existing = window.ownedWindows.filterIsInstance<HeavyweightPopupForcer>().filter { it.isDisplayable }
    if (force) {
      if (existing.isEmpty() && System.getProperty("jna.force_hw_popups", "true").toBoolean()) HeavyweightPopupForcer(window)
    }
    else existing.forEach { it.dispose() }
  }

  private class HeavyweightPopupForcer(owner: Window) : Window(owner) {
    private var packed = false

    init {
      pack()
      packed = true
    }

    override fun isVisible(): Boolean = packed && isDisplayable

    override fun getBounds(): Rectangle = owner.bounds
  }
}
