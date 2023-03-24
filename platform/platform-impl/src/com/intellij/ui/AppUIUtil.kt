// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "ReplaceGetOrSet")

package com.intellij.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.ConsentSettingsUi
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.*
import com.intellij.openapi.util.IconLoader.setUseDarkIcons
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.AppIcon.MacAppIcon
import com.intellij.ui.icons.findSvgData
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.loadWithSizes
import com.intellij.util.IconUtil
import com.intellij.util.ImageLoader.loadFromResource
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import sun.awt.AWTAccessor
import java.awt.*
import java.awt.event.ActionEvent
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.function.Predicate
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.border.Border

private const val VENDOR_PREFIX = "jetbrains-"
private var ourIcons: MutableList<Image?>? = null

@Volatile
private var isMacDocIconSet = false

private val LOG: Logger
  get() = logger<AppUIUtil>()

fun updateAppWindowIcon(window: Window) {
  if (AppUIUtil.isWindowIconAlreadyExternallySet) {
    return
  }

  var images = ourIcons
  if (images == null) {
    images = ArrayList(3)
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    val svgIconUrl = appInfo.applicationSvgIconUrl
    val smallSvgIconUrl = appInfo.smallApplicationSvgIconUrl
    val scaleContext = ScaleContext.create(window)
    if (SystemInfoRt.isUnix) {
      loadApplicationIconImage(svgPath = svgIconUrl, scaleContext = scaleContext, size = 128)?.let {
        images.add(it)
      }
    }
    val element = loadApplicationIconImage(svgPath = smallSvgIconUrl, scaleContext = scaleContext, size = 32)
    if (element != null) {
      images.add(element)
    }
    if (SystemInfoRt.isWindows) {
      @Suppress("DEPRECATION")
      loadApplicationIconImage(svgPath = smallSvgIconUrl,
                               scaleContext = scaleContext,
                               size = 16,
                               fallbackPath = appInfo.smallIconUrl)?.let {
        images.add(it)
      }
    }
    for (i in images.indices) {
      val image = images[i]
      if (image is JBHiDPIScaledImage) {
        images.set(i, image.delegate)
      }
    }

    ourIcons = images
  }

  if (!images.isEmpty()) {
    if (!SystemInfoRt.isMac) {
      window.iconImages = images
    }
    else if (!isMacDocIconSet) {
      MacAppIcon.setDockIcon(ImageUtil.toBufferedImage(images.first()!!))
      isMacDocIconSet = true
    }
  }
}

/**
 * Returns a hidpi-aware image.
 */
private fun loadApplicationIconImage(svgPath: String?, scaleContext: ScaleContext, size: Int, fallbackPath: String? = null): Image? {
  val image = if (svgPath == null) null else loadAppIconImage(svgPath, scaleContext, size)
  return image ?: loadFromResource(path = fallbackPath ?: return null, aClass = AppUIUtil::class.java)
}

private fun loadAppIconImage(svgPath: String, scaleContext: ScaleContext, size: Int): Image? {
  val pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
  val svgData = findSvgData(path = svgPath, classLoader = AppUIUtil::class.java.classLoader, pixScale = pixScale)
  if (svgData == null) {
    LOG.warn("Cannot load SVG application icon from $svgPath")
    return null
  }
  return loadWithSizes(sizes = listOf(size), data = svgData, scale = pixScale).first()
}

fun loadSmallApplicationIcon(scaleContext: ScaleContext,
                             size: Int = 16,
                             isReleaseIcon: Boolean = !ApplicationInfoImpl.getShadowInstance().isEAP): Icon {
  val appInfo = ApplicationInfoImpl.getShadowInstance()
  var smallIconUrl = appInfo.smallApplicationSvgIconUrl
  if (isReleaseIcon && appInfo.isEAP && appInfo is ApplicationInfoImpl) {
    // This is the way to load the release icon in EAP. Needed for some actions.
    smallIconUrl = appInfo.getSmallApplicationSvgIconUrl(false)
  }

  var image = if (smallIconUrl == null) null else loadAppIconImage(smallIconUrl, scaleContext, size)
  if (image != null) {
    return JBImageIcon(image)
  }

  @Suppress("DEPRECATION")
  val fallbackSmallIconUrl = appInfo.smallIconUrl
  image = loadFromResource(fallbackSmallIconUrl, AppUIUtil::class.java) ?: error("Can't load '$fallbackSmallIconUrl'")

  val icon = JBImageIcon(image)
  val width = icon.iconWidth
  if (width == size) {
    return icon
  }

  val scale = size / width.toFloat()
  return IconUtil.scale(icon = icon, ancestor = null, scale = scale)
}

fun findAppIcon(): String? {
  val binPath = PathManager.getBinPath()
  val binFiles = File(binPath).list()
  if (binFiles != null) {
    for (child in binFiles) {
      if (child.endsWith(".svg")) {
        return "$binPath/$child"
      }
    }
  }
  val svgIconUrl = ApplicationInfoImpl.getShadowInstance().applicationSvgIconUrl
  if (svgIconUrl != null) {
    val url = ApplicationInfoEx::class.java.getResource(svgIconUrl)
    if (url != null && URLUtil.FILE_PROTOCOL == url.protocol) {
      return URLUtil.urlToFile(url).absolutePath
    }
  }
  return null
}

object AppUIUtil {
  @Suppress("MemberVisibilityCanBePrivate")
  val isWindowIconAlreadyExternallySet: Boolean
    get() {
      if (SystemInfoRt.isMac) {
        return isMacDocIconSet || !PlatformUtils.isJetBrainsClient() && !PluginManagerCore.isRunningFromSources()
      }
      else {
        return SystemInfoRt.isWindows && java.lang.Boolean.getBoolean("ide.native.launcher") && SystemInfo.isJetBrainsJvm
      }
    }

  // todo[tav] JBR supports loading icon resource (id=2000) from the exe launcher, remove when OpenJDK supports it as well
  @JvmOverloads
  @JvmStatic
  fun loadSmallApplicationIcon(scaleContext: ScaleContext, size: Int = 16): Icon {
    return loadSmallApplicationIcon(scaleContext = scaleContext,
                                    size = size,
                                    isReleaseIcon = !ApplicationInfoImpl.getShadowInstance().isEAP)
  }

  @JvmStatic
  fun loadApplicationIcon(ctx: ScaleContext, size: Int): Icon? {
    val url = ApplicationInfoImpl.getShadowInstance().applicationSvgIconUrl
    return if (url == null) null else loadAppIconImage(url, ctx, size)?.let { JBImageIcon(it) }
  }

  @JvmStatic
  fun invokeLaterIfProjectAlive(project: Project, runnable: Runnable) {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      if (project.isOpen && !project.isDisposed) {
        runnable.run()
      }
    }
    else {
      application.invokeLater(runnable) { !project.isOpen || project.isDisposed }
    }
  }

  @JvmStatic
  fun invokeOnEdt(runnable: Runnable) {
    @Suppress("DEPRECATION")
    invokeOnEdt(runnable = runnable, expired = null)
  }

  @JvmStatic
  @Deprecated("Use {@link com.intellij.openapi.application.AppUIExecutor#expireWith(Disposable)}")
  fun invokeOnEdt(runnable: Runnable, expired: Condition<*>?) {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      if (expired == null || !expired.value(null)) {
        runnable.run()
      }
    }
    else if (expired == null) {
      application.invokeLater(runnable)
    }
    else {
      application.invokeLater(runnable, expired)
    }
  }

  @JvmStatic
  fun getFrameClass(): String {
    val name = ApplicationNamesInfo.getInstance().fullProductNameWithEdition.lowercase()
      .replace(' ', '-')
      .replace("intellij-idea", "idea").replace("android-studio", "studio") // backward compatibility
      .replace("-community-edition", "-ce").replace("-ultimate-edition", "").replace("-professional-edition", "")
    var wmClass = if (name.startsWith(VENDOR_PREFIX)) name else VENDOR_PREFIX + name
    if (PluginManagerCore.isRunningFromSources()) wmClass += "-debug"
    return wmClass
  }

  @JvmStatic
  fun showConsentsAgreementIfNeeded(log: Logger, filter: Predicate<in Consent?>): Boolean {
    val (first, second) = ConsentOptions.getInstance().getConsents(filter)
    if (!second) {
      return false
    }
    else if (EventQueue.isDispatchThread()) {
      return confirmConsentOptions(first)
    }
    else {
      var result = false
      try {
        EventQueue.invokeAndWait { result = confirmConsentOptions(first) }
      }
      catch (e: InterruptedException) {
        log.warn(e)
      }
      catch (e: InvocationTargetException) {
        log.warn(e)
      }
      return result
    }
  }

  @JvmStatic
  fun updateForDarcula(isDarcula: Boolean) {
    JBColor.setDark(isDarcula)
    setUseDarkIcons(isDarcula)
  }

  @JvmStatic
  fun confirmConsentOptions(consents: List<Consent>): Boolean {
    if (consents.isEmpty()) {
      return false
    }

    val ui = ConsentSettingsUi(false)
    val dialog = object : DialogWrapper(true) {
      override fun createContentPaneBorder(): Border? = null

      override fun createSouthPanel(): JComponent? {
        val southPanel = super.createSouthPanel()
        if (southPanel != null) {
          southPanel.border = createDefaultBorder()
        }
        return southPanel
      }

      override fun createCenterPanel() = ui.component

      override fun createActions(): Array<Action> {
        if (consents.size > 1) {
          val actions = super.createActions()
          setOKButtonText(IdeBundle.message("button.save"))
          setCancelButtonText(IdeBundle.message("button.skip"))
          return actions
        }
        setOKButtonText(consents.iterator().next().name)
        return arrayOf(okAction, object : DialogWrapperAction(IdeBundle.message("button.do.not.send")) {
          override fun doAction(e: ActionEvent) {
            close(NEXT_USER_EXIT_CODE)
          }
        })
      }

      override fun createDefaultActions() {
        super.createDefaultActions()
        init()
        isAutoAdjustable = false
      }
    }
    ui.reset(consents)
    dialog.isModal = true
    dialog.title = IdeBundle.message("dialog.title.data.sharing")
    dialog.pack()
    if (consents.size < 2) {
      dialog.setSize(dialog.window.width, dialog.window.height + scale(75))
    }
    dialog.show()
    val exitCode = dialog.exitCode
    if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
      return false // don't save any changes in this case: a user hasn't made a choice
    }
    val result: List<Consent>
    if (consents.size == 1) {
      result = listOf(consents.iterator().next().derive(exitCode == DialogWrapper.OK_EXIT_CODE))
    }
    else {
      result = ArrayList()
      ui.apply(result)
    }
    saveConsents(result)
    return true
  }

  @JvmStatic
  fun loadConsentsForEditing(): List<Consent> {
    val options = ConsentOptions.getInstance()
    var result = options.consents.first
    if (options.isEAP) {
      val statConsent = options.defaultUsageStatsConsent
      if (statConsent != null) {
        // init stats consent for EAP from the dedicated location
        val consents = result
        result = ArrayList()
        result.add(statConsent.derive(UsageStatisticsPersistenceComponent.getInstance().isAllowed))
        result.addAll(consents)
      }
    }
    return result
  }

  @JvmStatic
  fun saveConsents(consents: List<Consent>) {
    if (consents.isEmpty()) {
      return
    }

    val options = ConsentOptions.getInstance()
    if (ApplicationManager.getApplication() != null && options.isEAP) {
      val isUsageStats = ConsentOptions.condUsageStatsConsent()
      var saved = 0
      for (consent in consents) {
        if (isUsageStats.test(consent)) {
          UsageStatisticsPersistenceComponent.getInstance().isAllowed = consent.isAccepted
          saved++
        }
      }
      if (consents.size - saved > 0) {
        options.setConsents(consents.filter { !isUsageStats.test(it) })
      }
    }
    else {
      options.setConsents(consents)
    }
  }

  /**
   * Targets the component to a (screen) device before showing.
   * In case the component is already a part of the UI hierarchy (and is thus bound to a device), the method does nothing.
   *
   * The prior targeting to a device is required when there's a need to calculate the preferred size of a compound component
   * (such as `JEditorPane`, for instance) which is not yet added to a hierarchy.
   * The calculation in that case may involve device-dependent metrics (such as font metrics)
   * and thus should refer to a particular device in multi-monitor env.
   *
   * Note that if after calling this method the component is added to another hierarchy bound to a different device,
   * AWT will throw `IllegalArgumentException`.
   * To avoid that, the device should be reset by calling `targetToDevice(comp, null)`.
   *
   * @param target the component representing the UI hierarchy and the target device
   * @param comp the component to target
   */
  @JvmStatic
  fun targetToDevice(comp: Component, target: Component?) {
    if (comp.isShowing) {
      return
    }
    val gc = target?.graphicsConfiguration
    AWTAccessor.getComponentAccessor().setGraphicsConfiguration(comp, gc)
  }

  @JvmStatic
  fun isInFullScreen(window: Window?): Boolean = window is IdeFrame && (window as IdeFrame).isInFullScreen

  fun adjustFractionalMetrics(defaultValue: Any): Any {
    if (!SystemInfoRt.isMac || GraphicsEnvironment.isHeadless()) {
      return defaultValue
    }
    val gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    return if (sysScale(gc) == 1.0f) RenderingHints.VALUE_FRACTIONALMETRICS_OFF else defaultValue
  }
}
