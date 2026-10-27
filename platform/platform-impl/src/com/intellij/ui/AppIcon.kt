// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.AppIconScheme
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.X11UiUtil
import com.intellij.ui.paint.PaintUtil
import com.intellij.util.IconUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.EDT
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.MultiResolutionImageProvider
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.imaging.common.BinaryOutputStream
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.Window
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteOrder
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.math.abs

private val LOG = logger<AppIcon>()

/**
 * The class must be accessed only from EDT.
 */
abstract class AppIcon {
  companion object {
    private var instance: AppIcon? = null

    @JvmStatic
    fun getInstance(): AppIcon = instance ?: createInstance().also { instance = it }

    private fun createInstance(): AppIcon = when {
      GraphicsEnvironment.isHeadless() || GraphicsUtil.isRemoteEnvironment() -> EmptyIcon()
      SystemInfoRt.isMac -> MacAppIcon()
      StartupUiUtil.isXToolkit() -> XAppIcon()
      StartupUiUtil.isWaylandToolkit() -> WLAppIcon()
      SystemInfoRt.isWindows && Win7TaskBar.isAvailable() -> Win7AppIcon()
      else -> EmptyIcon()
    }
  }

  abstract fun setProgress(project: Project?, @NonNls processId: Any?, scheme: AppIconScheme.Progress, value: Double, isOk: Boolean): Boolean

  abstract fun hideProgress(project: Project?, @NonNls processId: Any?): Boolean

  abstract fun setErrorBadge(project: Project?, text: String?)

  abstract fun setOkBadge(project: Project?, visible: Boolean)

  abstract fun requestAttention(project: Project?, critical: Boolean)

  /**
   * This method requests the OS to activate the specified application window.
   * This might cause the window to steal focus from another (currently active) application, which is generally not considered acceptable.
   * So it should be used only in special cases, when we know that the user definitely expects such behaviour.
   * In most cases, requesting focus in the target window via the standard AWT mechanism and [requestAttention]
   * should be used instead, to request user attention but not switch the focus to it automatically.
   *
   * This method might resort to requesting user attention to a target window if focus stealing is not supported by the OS
   * (this is the case on Windows, where focus stealing can only be enabled using [WinFocusStealer]).
   *
   * @see requestFocus
   */
  open fun requestFocus(frame: IdeFrame?) {
    requestFocus(frame?.let { SwingUtilities.getWindowAncestor(it.component) })
  }

  /**
   * @see requestFocus
   */
  open fun requestFocus(window: Window?) {
  }

  open fun requestFocus() {
  }

  internal abstract class BaseIcon : AppIcon() {
    private var appListener: ApplicationActivationListener? = null
    protected var currentProcessId: Any? = null
    protected var lastValue: Double = 0.0

    final override fun setProgress(project: Project?, processId: Any?, scheme: AppIconScheme.Progress, value: Double, isOk: Boolean): Boolean {
      return !isAppActive() && isProgressAppIconEnabled() && (currentProcessId == null || currentProcessId == processId) &&
             doSetProgress(getIdeFrame(project), processId, scheme, value, isOk)
    }

    final override fun hideProgress(project: Project?, processId: Any?): Boolean {
      return isProgressAppIconEnabled() && doHideProgress(getIdeFrame(project), processId)
    }

    final override fun setErrorBadge(project: Project?, text: String?) {
      if (!isAppActive() && Registry.`is`("ide.appIcon.badge")) {
        doSetOkBadge(getIdeFrame(project), false)
        doSetTextBadge(getIdeFrame(project), text)
      }
    }

    final override fun setOkBadge(project: Project?, visible: Boolean) {
      if (!isAppActive() && Registry.`is`("ide.appIcon.badge")) {
        doSetTextBadge(getIdeFrame(project), null)
        doSetOkBadge(getIdeFrame(project), visible)
      }
    }

    final override fun requestAttention(project: Project?, critical: Boolean) {
      if (!isAppActive() && Registry.`is`("ide.appIcon.requestAttention")) {
        doRequestAttention(getIdeFrame(project), critical)
      }
    }

    protected abstract fun doSetProgress(frame: JFrame?, processId: Any?, scheme: AppIconScheme.Progress, value: Double, isOk: Boolean): Boolean

    protected abstract fun doHideProgress(frame: JFrame?, processId: Any?): Boolean

    protected abstract fun doSetTextBadge(frame: JFrame?, text: String?)

    protected abstract fun doSetOkBadge(frame: JFrame?, visible: Boolean)

    protected abstract fun doRequestAttention(frame: JFrame?, critical: Boolean)

    private fun getIdeFrame(project: Project?): JFrame? = WindowManager.getInstance().getFrame(project)

    private fun isAppActive(): Boolean {
      val app = ApplicationManager.getApplication() ?: return false
      if (appListener == null) {
        val listener = object : ApplicationActivationListener {
          override fun applicationActivated(ideFrame: IdeFrame) {
            val frame = when (ideFrame) {
              is JFrame -> ideFrame
              is ProjectFrameHelper -> ideFrame.frame
              else -> null
            }

            if (isProgressAppIconEnabled()) {
              doHideProgress(frame, currentProcessId)
            }
            doSetOkBadge(frame, false)
            doSetTextBadge(frame, null)
          }
        }
        appListener = listener
        app.messageBus.connect().subscribe(ApplicationActivationListener.TOPIC, listener)
      }
      return app.isActive
    }
  }

  @Suppress("UseJBColor")
  internal class MacAppIcon : BaseIcon() {
    companion object {
      // white 80% transparent
      private val PROGRESS_BACKGROUND_COLOR = Color(255, 255, 255, 217)
      private val PROGRESS_OUTLINE_COLOR = Color(140, 139, 140)

      /**
       * Renders the dock icon into a plain image.
       */
      @RequiresBackgroundThread
      private fun loadAppImage(): BufferedImage? {
        try {
          if (!Taskbar.isTaskbarSupported()) return null
          val appImage = Taskbar.getTaskbar().iconImage ?: return null

          // [tav] expecting two resolution variants for the dock icon: 128x128, 256x256
          return ImageUtil.toBufferedImage(MultiResolutionImageProvider.getMaxSizeResolutionVariant(appImage))
        }
        catch (e: Exception) {
          rethrowControlFlowException(e)
          LOG.error(e)
          return null
        }
      }

      private fun createAppImage(appImage: BufferedImage): Pair<BufferedImage, Graphics2D> {
        @Suppress("UndesirableClassUsage")
        val current = BufferedImage(appImage.width, appImage.height, BufferedImage.TYPE_INT_ARGB)
        val g = current.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        StartupUiUtil.drawImage(g, appImage, 0, 0, null)
        return current to g
      }

      fun setDockIcon(image: BufferedImage) {
        try {
          if (!Taskbar.isTaskbarSupported()) return
          Taskbar.getTaskbar().iconImage = image
        }
        catch (e: Exception) {
          LOG.error(e)
        }
      }
    }

    private var appImage: BufferedImage? = null
    private var appImageRequested = false
    private val progressImagesCache = HashMap<Any?, Pair<BufferedImage, Graphics2D>>()

    /**
     * Returns the dock icon image, or `null` until a background thread loads it.
     * The first call starts the load. The EDT stores the result.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun getOrLoadAppImage(): BufferedImage? {
      EDT.assertIsEdt()

      if (appImage == null && !appImageRequested) {
        appImageRequested = true
        GlobalScope.launch {
          val loadedImage = loadAppImage() ?: return@launch
          withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
            appImage = loadedImage
          }
        }
      }
      return appImage
    }

    override fun doSetTextBadge(frame: JFrame?, text: String?) {
      EDT.assertIsEdt()

      try {
        if (!Taskbar.isTaskbarSupported()) return
        Taskbar.getTaskbar().setIconBadge(text)
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }

    override fun requestFocus(window: Window?) {
      EDT.assertIsEdt()

      if (window != null) {
        window.toFront()

        try {
          Desktop.getDesktop().requestForeground(false)
        }
        catch (e: Exception) {
          LOG.error(e)
        }
      }
      else {
        requestFocus()
      }
    }

    override fun requestFocus() {
      EDT.assertIsEdt()

      try {
        Desktop.getDesktop().requestForeground(true)
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }

    override fun doRequestAttention(frame: JFrame?, critical: Boolean) {
      EDT.assertIsEdt()

      try {
        if (!Taskbar.isTaskbarSupported()) return
        Taskbar.getTaskbar().requestUserAttention(true, critical)
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }

    override fun doHideProgress(frame: JFrame?, processId: Any?): Boolean {
      EDT.assertIsEdt()

      if (currentProcessId != null && currentProcessId != processId) {
        return false
      }

      val appImage = getOrLoadAppImage() ?: return false

      setDockIcon(appImage)
      progressImagesCache.remove(currentProcessId)
      currentProcessId = null
      lastValue = 0.0
      return true
    }

    override fun doSetOkBadge(frame: JFrame?, visible: Boolean) {
      EDT.assertIsEdt()

      val appImage = getOrLoadAppImage() ?: return
      val (image, g) = createAppImage(appImage)

      if (visible) {
        var okIcon = AllIcons.Mac.AppIconOk512
        val w = image.width
        if (w != 128) {
          okIcon = IconUtil.scale(okIcon, frame?.rootPane, w / 128f)
        }

        val x = w - okIcon.iconWidth
        val y = 0

        okIcon.paintIcon(JOptionPane.getRootFrame(), g, x, y)
      }

      setDockIcon(image)
    }

    override fun doSetProgress(frame: JFrame?, processId: Any?, scheme: AppIconScheme.Progress, value: Double, isOk: Boolean): Boolean {
      EDT.assertIsEdt()

      val appImage = getOrLoadAppImage() ?: return false

      currentProcessId = processId

      if (lastValue > value) return true

      if (abs(lastValue - value) < 0.02) return true

      try {
        val progressHeight = appImage.height * 0.13
        val xInset = appImage.width * 0.05
        val yInset = appImage.height * 0.15

        val width = appImage.width - xInset * 2
        val y = appImage.height - progressHeight - yInset

        val borderArea = Area(RoundRectangle2D.Double(
          xInset - 1, y - 1, width + 2, progressHeight + 2,
          progressHeight + 2, progressHeight + 2,
        ))

        val backgroundArea = Area(Rectangle2D.Double(xInset, y, width, progressHeight))

        backgroundArea.intersect(borderArea)

        val progressArea = Area(Rectangle2D.Double(xInset + 1, y + 1, (width - 2) * value, progressHeight - 1))

        progressArea.intersect(borderArea)

        val (image, g2d) = progressImagesCache.getOrPut(currentProcessId) { createAppImage(appImage) }

        g2d.color = PROGRESS_BACKGROUND_COLOR
        g2d.fill(backgroundArea)
        g2d.color = if (isOk) scheme.okColor else scheme.errorColor
        g2d.fill(progressArea)
        g2d.color = PROGRESS_OUTLINE_COLOR
        g2d.draw(backgroundArea)
        g2d.draw(borderArea)

        setDockIcon(image)

        lastValue = value
      }
      catch (e: Exception) {
        LOG.error(e)
      }
      finally {
        currentProcessId = null
      }

      return true
    }
  }

  @Suppress("UseJBColor")
  private class Win7AppIcon : BaseIcon() {
    companion object {
      private val errorBadgeShadowColor = Color(0, 0, 0, 102)
      private val errorBadgeMainColor = Color(255, 98, 89)
      private val errorBadgeTextBackgroundColor = Color(0, 0, 0, 39)

      @Throws(IOException::class)
      private fun writeTransparentIco(src: BufferedImage): ByteArray {
        LOG.assertTrue(BufferedImage.TYPE_INT_ARGB == src.type || BufferedImage.TYPE_4BYTE_ABGR == src.type)

        val bitCount = 32

        ByteArrayOutputStream().use { os ->
          BinaryOutputStream(os, ByteOrder.LITTLE_ENDIAN).use { bos ->
            var scanLineSize = (bitCount * src.width + 7) / 8
            if (scanLineSize % 4 != 0) {
              scanLineSize += 4 - scanLineSize % 4 // pad scan line to 4 byte size.
            }
            var tScanLineSize = (src.width + 7) / 8
            if (tScanLineSize % 4 != 0) {
              tScanLineSize += 4 - tScanLineSize % 4 // pad scan line to 4 byte size.
            }
            val imageSize = 40 + src.height * scanLineSize + src.height * tScanLineSize

            // ICONDIR
            bos.write2Bytes(0) // reserved
            bos.write2Bytes(1) // 1=ICO, 2=CUR
            bos.write2Bytes(1) // count

            // ICONDIRENTRY
            var iconDirEntryWidth = src.width
            var iconDirEntryHeight = src.height
            if (iconDirEntryWidth > 255 || iconDirEntryHeight > 255) {
              iconDirEntryWidth = 0
              iconDirEntryHeight = 0
            }
            bos.write(iconDirEntryWidth)
            bos.write(iconDirEntryHeight)
            bos.write(0)
            bos.write(0) // reserved
            bos.write2Bytes(1) // color planes
            bos.write2Bytes(bitCount)
            bos.write4Bytes(imageSize)
            bos.write4Bytes(22) // image offset

            // BITMAPINFOHEADER
            bos.write4Bytes(40) // size
            bos.write4Bytes(src.width)
            bos.write4Bytes(2 * src.height)
            bos.write2Bytes(1) // planes
            bos.write2Bytes(bitCount)
            bos.write4Bytes(0) // compression
            bos.write4Bytes(0) // image size
            bos.write4Bytes(0) // x pixels per meter
            bos.write4Bytes(0) // y pixels per meter
            bos.write4Bytes(0) // colors used, 0 = (1 << bitCount) (ignored)
            bos.write4Bytes(0) // colors important

            var bitCache = 0
            var bitsInCache = 0
            val rowPadding = scanLineSize - (bitCount * src.width + 7) / 8
            for (y in src.height - 1 downTo 0) {
              for (x in 0 until src.width) {
                val argb = src.getRGB(x, y)

                bos.write(0xff and argb)
                bos.write(0xff and (argb shr 8))
                bos.write(0xff and (argb shr 16))
                bos.write(0xff and (argb shr 24))
              }

              repeat(rowPadding) { bos.write(0) }
            }

            val tRowPadding = tScanLineSize - (src.width + 7) / 8
            for (y in src.height - 1 downTo 0) {
              for (x in 0 until src.width) {
                val argb = src.getRGB(x, y)
                val alpha = 0xff and (argb shr 24)
                bitCache = bitCache shl 1
                if (alpha == 0) {
                  bitCache = bitCache or 1
                }
                bitsInCache++
                if (bitsInCache >= 8) {
                  bos.write(0xff and bitCache)
                  bitCache = 0
                  bitsInCache = 0
                }
              }

              if (bitsInCache > 0) {
                bitCache = bitCache shl (8 - bitsInCache)
                bos.write(0xff and bitCache)
                bitCache = 0
                bitsInCache = 0
              }

              repeat(tRowPadding) { bos.write(0) }
            }

            return os.toByteArray()
          }
        }
      }

      private fun displayableFrame(frame: JFrame?): JFrame? = frame?.takeIf { it.isDisplayable }
    }

    private var okIcon: Long = 0

    override fun doSetProgress(frame: JFrame?, processId: Any?, scheme: AppIconScheme.Progress, value: Double, isOk: Boolean): Boolean {
      EDT.assertIsEdt()

      currentProcessId = processId

      if (abs(lastValue - value) < 0.02) {
        return true
      }

      try {
        displayableFrame(frame)?.let { Win7TaskBar.setProgress(it, value, isOk) }
      }
      catch (e: Throwable) {
        LOG.error(e)
      }

      lastValue = value
      currentProcessId = null
      return true
    }

    override fun doHideProgress(frame: JFrame?, processId: Any?): Boolean {
      EDT.assertIsEdt()
      if (currentProcessId != null && currentProcessId != processId) {
        return false
      }

      try {
        displayableFrame(frame)?.let { Win7TaskBar.hideProgress(it) }
      }
      catch (e: Throwable) {
        LOG.error(e)
      }

      currentProcessId = null
      lastValue = 0.0
      return true
    }

    override fun doSetTextBadge(frame: JFrame?, text: String?) {
      EDT.assertIsEdt()
      val validFrame = displayableFrame(frame) ?: return

      var icon = 0L

      if (text != null) {
        try {
          val size = 16
          val image = UIUtil.createImage(validFrame.rootPane, size, size, BufferedImage.TYPE_INT_ARGB)
          PaintUtil.use(image.createGraphics()) { g ->
            val shadowRadius = 16
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.paint = errorBadgeShadowColor
            g.fillRoundRect(0, 0, shadowRadius, shadowRadius, size, size)

            val mainRadius = 14
            g.paint = errorBadgeMainColor
            g.fillRoundRect(size / 2 - mainRadius / 2, size / 2 - mainRadius / 2, mainRadius, mainRadius, size, size)

            val font = g.font
            g.font = Font(font.name, Font.BOLD, 9)
            val fontMetrics = g.fontMetrics

            val textWidth = fontMetrics.stringWidth(text)
            val textHeight = UIUtil.getHighestGlyphHeight(text, font, g)

            g.paint = errorBadgeTextBackgroundColor
            g.fillOval(size / 2 - textWidth / 2, size / 2 - textHeight / 2, textWidth, textHeight)

            g.color = Color.white
            g.drawString(text, size / 2 - textWidth / 2, size / 2 - fontMetrics.height / 2 + fontMetrics.ascent)
          }

          icon = Win7TaskBar.createIcon(writeTransparentIco(image))
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }

      try {
        Win7TaskBar.setOverlayIcon(validFrame, icon, icon != 0L)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }

    override fun doSetOkBadge(frame: JFrame?, visible: Boolean) {
      EDT.assertIsEdt()
      val validFrame = displayableFrame(frame) ?: return

      var icon = 0L

      if (visible) {
        icon = synchronized(Win7AppIcon::class.java) {
          if (okIcon == 0L) {
            try {
              val image = ImageIO.read(requireNotNull(AppIcon::class.java.getResourceAsStream("/mac/appIconOk512.png")))
              okIcon = Win7TaskBar.createIcon(writeTransparentIco(image))
            }
            catch (e: Throwable) {
              LOG.error(e)
              okIcon = 0
            }
          }

          okIcon
        }
      }

      try {
        Win7TaskBar.setOverlayIcon(validFrame, icon, false)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }

    override fun doRequestAttention(frame: JFrame?, critical: Boolean) {
      EDT.assertIsEdt()
      try {
        displayableFrame(frame)?.let { Win7TaskBar.attention(it) }
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }

    override fun requestFocus(window: Window?) {
      if (window == null) {
        return
      }

      if (window is Frame) {
        window.state = Frame.NORMAL
      }
      try {
        // For SetForegroundWindow WinAPI method to 'steal' focus from other apps more reliably, it shouldn't be called immediately after
        // other window- or focus-related API methods (e.g. BringWindowToTop, which is invoked by java.awt.Component.requestFocus()).
        // Required delay is empirically estimated to be around 20 ms.
        // The hypothesis is that timestamps used for checking foreground lock timeout are determined with a certain inaccuracy, caused by
        // timer resolution, and functions like BringWindowToTop can be treated as a user input, so SetForegroundWindow call fails to
        // focus a background window, even with foreground lock timeout is set to 0, if it's preceded by BringWindowToTop call.
        // See also WinFocusStealer class's javadoc for details.
        Thread.sleep(Registry.intValue("win.request.focus.delay.ms").toLong())
      }
      catch (e: InterruptedException) {
        LOG.error(e)
      }
      Win7TaskBar.setForegroundWindow(window)
    }
  }

  private class XAppIcon : BaseIcon() {
    override fun doSetProgress(frame: JFrame?, processId: Any?, scheme: AppIconScheme.Progress, value: Double, isOk: Boolean): Boolean = false

    override fun doHideProgress(frame: JFrame?, processId: Any?): Boolean = false

    override fun doSetTextBadge(frame: JFrame?, text: String?) {}

    override fun doSetOkBadge(frame: JFrame?, visible: Boolean) {}

    override fun doRequestAttention(frame: JFrame?, critical: Boolean) {
      if (frame != null) X11UiUtil.requestAttention(frame)
    }

    override fun requestFocus(window: Window?) {
      if (window != null) X11UiUtil.activate(window)
    }
  }

  private class WLAppIcon : BaseIcon() {
    override fun doSetProgress(frame: JFrame?, processId: Any?, scheme: AppIconScheme.Progress, value: Double, isOk: Boolean): Boolean = false

    override fun doHideProgress(frame: JFrame?, processId: Any?): Boolean = false

    override fun doSetTextBadge(frame: JFrame?, text: String?) {}

    override fun doSetOkBadge(frame: JFrame?, visible: Boolean) {}

    override fun doRequestAttention(frame: JFrame?, critical: Boolean) {
      // TODO: perhaps, use
      // https://wayland.app/protocols/gtk-shell#gtk_surface1:request:request_focus
    }

    override fun requestFocus(window: Window?) {
      window?.toFront()
    }
  }

  private class EmptyIcon : AppIcon() {
    override fun setProgress(project: Project?, processId: Any?, scheme: AppIconScheme.Progress, value: Double, isOk: Boolean): Boolean = false

    override fun hideProgress(project: Project?, processId: Any?): Boolean = false

    override fun setErrorBadge(project: Project?, text: String?) {}

    override fun setOkBadge(project: Project?, visible: Boolean) {}

    override fun requestAttention(project: Project?, critical: Boolean) {}
  }
}

private fun isProgressAppIconEnabled(): Boolean = SystemProperties.getBooleanProperty("ide.appIcon.progress", true)
