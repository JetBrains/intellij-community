// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "DeprecatedCallableAddReplaceWith", "LiftReturnOrAssignment")

package com.intellij.openapi.util

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.*
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ReflectionUtil
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.ImageFilter
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent
import kotlin.Pair

private val LOG: Logger
  get() = logger<IconLoader>()

private val iconCache = Caffeine.newBuilder()
  .expireAfterAccess(30, TimeUnit.MINUTES)
  .executor(Dispatchers.Default.asExecutor())
  .maximumSize(256)
  .build<Pair<String, ClassLoader?>, CachedImageIcon>()

internal val fakeComponent: JComponent by lazy { object : JComponent() {} }

/**
 * Provides access to icons used in the UI.
 * Please see [Icons](https://plugins.jetbrains.com/docs/intellij/icons.html)
 * about supported formats, organization, and accessing icons in plugins.
 *
 * @see com.intellij.util.IconUtil
 */
object IconLoader {
  @JvmStatic
  fun installPathPatcher(patcher: IconPathPatcher) {
    updateTransform { it.withPathPatcher(patcher) }
  }

  @Internal
  fun installPostPathPatcher(patcher: IconPathPatcher) {
    updateTransform { it.withPostPathPatcher(patcher) }
  }

  @JvmStatic
  fun removePathPatcher(patcher: IconPathPatcher) {
    updateTransform { it.withoutPathPatcher(patcher) }
  }

  @JvmStatic
  fun setUseDarkIcons(useDarkIcons: Boolean) {
    updateTransform { it.withDark(useDarkIcons) }
  }

  @JvmStatic
  fun setFilter(filter: ImageFilter) {
    updateTransform { it.withFilter(filter) }
  }

  @JvmStatic
  fun clearCache() {
    pathTransformGlobalModCount.incrementAndGet()
    clearCacheOnUpdateTransform()
  }

  @TestOnly
  fun clearCacheInTests() {
    iconCache.invalidateAll()
    clearCacheOnUpdateTransform()
    pathTransformGlobalModCount.incrementAndGet()
  }

  @Deprecated("Use {@link #getIcon(String, ClassLoader)}", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun getIcon(path: @NonNls String): Icon {
    return getIcon(path = path, aClass = ReflectionUtil.getGrandCallerClass() ?: error(path))
  }

  @Deprecated("Use {@link #findIcon(String, ClassLoader)}.", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun findIcon(path: @NonNls String): Icon? {
    val callerClass = ReflectionUtil.getGrandCallerClass() ?: return null
    return findIcon(path = path, classLoader = callerClass.classLoader)
  }

  @JvmStatic
  fun getIcon(path: String, aClass: Class<*>): Icon {
    return findIconUsingDeprecatedImplementation(originalPath = path, classLoader = aClass.classLoader, aClass = aClass, strict = false)
           ?: throw IllegalStateException("Icon cannot be found in '$path', class='${aClass.name}'")
  }

  @JvmStatic
  fun getIcon(path: String, classLoader: ClassLoader): Icon {
    return findIcon(path = path, classLoader = classLoader)
           ?: throw IllegalStateException("Icon cannot be found in '$path', classLoader='$classLoader'")
  }


  @TestOnly
  @JvmStatic
  fun activate() {
    isIconActivated = true
  }

  @TestOnly
  @JvmStatic
  fun deactivate() {
    isIconActivated = false
  }

  /**
   * Might return null if the icon was not found.
   * Use only if you expected null return value, otherwise see [IconLoader.getIcon]
   */
  @JvmStatic
  fun findIcon(path: String, aClass: Class<*>): Icon? {
    return findIconUsingNewImplementation(path = path, classLoader = aClass.classLoader)
  }

  @JvmStatic
  fun findIcon(path: String, aClass: Class<*>, deferUrlResolve: Boolean, strict: Boolean): Icon? {
    if (deferUrlResolve) {
      return findIconUsingDeprecatedImplementation(originalPath = path, classLoader = aClass.classLoader, aClass = aClass, strict = strict)
    }
    else {
      return findIconUsingNewImplementation(path, aClass.classLoader)
    }
  }

  @JvmStatic
  fun findIcon(url: URL?): Icon? {
    if (url == null) {
      return null
    }

    val key = Pair<String, ClassLoader?>(url.toString(), null)
    return iconCache.get(key) { CachedImageIcon(url = url) }
  }

  @JvmStatic
  fun findIcon(url: URL?, storeToCache: Boolean): Icon? {
    if (url == null) {
      return null
    }

    val key = Pair<String, ClassLoader?>(url.toString(), null)
    if (storeToCache) {
      return iconCache.get(key) { CachedImageIcon(url = url) }
    }
    else {
      return iconCache.getIfPresent(key) ?: CachedImageIcon(url = url)
    }
  }

  @Internal
  fun findUserIconByPath(file: Path): com.intellij.ui.icons.CachedImageIcon {
    return iconCache.get(Pair(file.toString(), null)) { CachedImageIcon(file = file) }
  }

  @JvmStatic
  fun findIcon(path: String, classLoader: ClassLoader): Icon? {
    return findIconUsingNewImplementation(path, classLoader)
  }

  @JvmStatic
  fun findResolvedIcon(path: String, classLoader: ClassLoader): Icon? {
    val icon = findIconUsingNewImplementation(path, classLoader)
    return if (icon is com.intellij.ui.icons.CachedImageIcon && icon.getRealIcon() === EMPTY_ICON) null else icon
  }

  @JvmOverloads
  @JvmStatic
  fun toImage(icon: Icon, scaleContext: ScaleContext? = null): Image? {
    var effectiveIcon = icon
    if (effectiveIcon is RetrievableIcon) {
      effectiveIcon = getOriginIcon(effectiveIcon)
    }
    if (effectiveIcon is com.intellij.ui.icons.CachedImageIcon) {
      return effectiveIcon.resolveImage(scaleContext = scaleContext)
    }
    if (effectiveIcon is ImageIcon) {
      return effectiveIcon.image
    }
    if (effectiveIcon.iconWidth <= 0 || effectiveIcon.iconHeight <= 0) {
      return null
    }

    val image: BufferedImage
    if (GraphicsEnvironment.isHeadless()) {
      // for testing purpose
      image = ImageUtil.createImage(scaleContext, effectiveIcon.iconWidth.toDouble(), effectiveIcon.iconHeight.toDouble(),
                                    BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.ROUND)
    }
    else {
      val effectiveScaleContext = scaleContext ?: ScaleContext.create()
      image = if (StartupUiUtil.isJreHiDPI(effectiveScaleContext)) {
        HiDPIImage(scaleContext = effectiveScaleContext,
                   width = effectiveIcon.iconWidth.toDouble(),
                   height = effectiveIcon.iconHeight.toDouble(),
                   type = BufferedImage.TYPE_INT_ARGB_PRE,
                   roundingMode = PaintUtil.RoundingMode.ROUND)
      }
      else {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
          .defaultScreenDevice.defaultConfiguration
          .createCompatibleImage(
            PaintUtil.RoundingMode.ROUND.round(effectiveScaleContext.apply(effectiveIcon.iconWidth.toDouble(), DerivedScaleType.DEV_SCALE)),
            PaintUtil.RoundingMode.ROUND.round(
              effectiveScaleContext.apply(effectiveIcon.iconHeight.toDouble(), DerivedScaleType.DEV_SCALE)),
            Transparency.TRANSLUCENT
          )
      }
    }

    val g = image.createGraphics()
    try {
      effectiveIcon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    return image
  }

  @JvmStatic
  fun isGoodSize(icon: Icon): Boolean = icon.iconWidth > 0 && icon.iconHeight > 0

  /**
   * Gets (creates if necessary) disabled icon based on the passed one.
   *
   * @return `ImageIcon` constructed from disabled image of passed icon.
   */
  @JvmStatic
  fun getDisabledIcon(icon: Icon): Icon = getDisabledIcon(icon = icon, disableFilter = null)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use com.intellij.ui.svg.colorPatchedIcon")
  @Internal
  fun colorPatchedIcon(icon: Icon, colorPatcher: SvgElementColorPatcherProvider): Icon {
    return com.intellij.ui.svg.colorPatchedIcon(icon = icon, colorPatcher = colorPatcher)
  }

  /**
   * Creates a new icon with the filter applied.
   */
  @Internal
  fun filterIcon(icon: Icon, filterSupplier: RgbImageFilterSupplier): Icon {
    val effectiveIcon = if (icon is LazyIcon) icon.getOrComputeIcon() else icon
    if (!checkIconSize(effectiveIcon)) {
      return EMPTY_ICON
    }
    return if (effectiveIcon is com.intellij.ui.icons.CachedImageIcon) {
      effectiveIcon.createWithFilter(filterSupplier)
    }
    else {
      FilteredIcon(baseIcon = effectiveIcon, filterSupplier = filterSupplier)
    }
  }

  @JvmStatic
  fun getTransparentIcon(icon: Icon): Icon {
    return getTransparentIcon(icon = icon, alpha = 0.5f)
  }

  @JvmStatic
  fun getTransparentIcon(icon: Icon, alpha: Float): Icon {
    return object : RetrievableIcon {
      override fun retrieveIcon(): Icon = icon

      override fun getIconHeight(): Int = icon.iconHeight

      override fun getIconWidth(): Int = icon.iconWidth

      override fun replaceBy(replacer: IconReplacer): Icon = getTransparentIcon(replacer.replaceIcon(icon), alpha)

      override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        GraphicsUtil.paintWithAlpha(g, alpha) { icon.paintIcon(c, g, x, y) }
      }
    }
  }

  @Deprecated("Not needed")
  @JvmStatic
  fun getIconSnapshot(icon: Icon): Icon {
    return if (icon is com.intellij.ui.icons.CachedImageIcon) icon.getRealIcon() else icon
  }

  /**
   * Returns a copy of the provided `icon` with darkness set to `dark`.
   * The method takes effect on a [CachedImageIcon] (or its wrapper) only.
   */
  @JvmStatic
  fun getDarkIcon(icon: Icon, dark: Boolean): Icon {
    val replacer = if (dark) ourDarkReplacer else ourLightReplacer
    return replacer.replaceIcon(icon)
  }

  fun detachClassLoader(classLoader: ClassLoader) {
    iconCache.asMap().entries.removeIf { (key, icon) ->
      icon.detachClassLoader(classLoader) || key.second === classLoader
    }
  }

  @JvmStatic
  fun createLazy(producer: Supplier<out Icon>): Icon = LazyIcon(producer)

  @Deprecated("Unused", ReplaceWith("com.intellij.ui.icons.CachedImageIcon"), DeprecationLevel.ERROR)
  open class CachedImageIcon private constructor(
    loader: ImageDataLoader,
  ) : com.intellij.ui.icons.CachedImageIcon(
    loader = loader,
    toolTip = null,
  )
}

@Internal
fun findIconUsingNewImplementation(path: String, classLoader: ClassLoader, toolTip: Supplier<String?>? = null): Icon? {
  return findIconByPath(path = path, classLoader = classLoader, cache = iconCache, toolTip = toolTip)
}

@Internal
fun findIconUsingDeprecatedImplementation(originalPath: String,
                                          classLoader: ClassLoader,
                                          aClass: Class<*>?,
                                          toolTip: Supplier<String?>? = null,
                                          strict: Boolean = false): Icon? {
  var effectiveClassLoader = classLoader
  val startTime = StartUpMeasurer.getCurrentTimeIfEnabled()
  val patchedPath = patchIconPath(originalPath = originalPath, classLoader = effectiveClassLoader)
  val effectivePath = patchedPath?.first ?: originalPath
  patchedPath?.second?.let {
    effectiveClassLoader = it
  }

  var icon: Icon?
  if (isReflectivePath(effectivePath)) {
    icon = getReflectiveIcon(path = effectivePath, classLoader = effectiveClassLoader)
  }
  else {
    val key = Pair(originalPath, effectiveClassLoader)
    icon = iconCache.getIfPresent(key)
    if (icon == null) {
      icon = iconCache.get(key) { k ->
        val loader = ImageDataByPathResourceLoader(path = effectivePath, ownerClass = aClass, classLoader = k.second, strict = strict)
        CachedImageIcon(loader = loader, toolTip = toolTip)
      }
    }
  }
  if (startTime != -1L) {
    IconLoadMeasurer.findIcon.end(startTime)
  }
  return icon
}

internal class LazyIcon(private val producer: Supplier<out Icon>) : CopyableIcon, RetrievableIcon {
  private var wasComputed = false

  @Volatile
  private var icon: Icon? = null

  private var transformModCount = pathTransformGlobalModCount.get()

  override fun isComplex(): Boolean = true

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    getOrComputeIcon().paintIcon(c, g, x, y)
  }

  override fun replaceBy(replacer: IconReplacer): Icon {
    return LazyIcon { replacer.replaceIcon(producer.get()) }
  }

  override fun getIconWidth(): Int = getOrComputeIcon().iconWidth

  override fun getIconHeight(): Int = getOrComputeIcon().iconHeight

  @Synchronized
  fun getOrComputeIcon(): Icon {
    var icon = icon
    val newTransformModCount = pathTransformGlobalModCount.get()
    if (icon != null && wasComputed && transformModCount == newTransformModCount) {
      return icon
    }

    transformModCount = newTransformModCount
    wasComputed = true
    icon = try {
      producer.get()
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error("Cannot compute icon", e)
      IconManager.getInstance().getPlatformIcon(PlatformIcons.Stub)
    }
    this.icon = icon
    return icon!!
  }

  override fun retrieveIcon(): Icon = getOrComputeIcon()

  override fun copy(): Icon = copyIcon(icon = getOrComputeIcon(), ancestor = null, deepCopy = false)
}

private val ourDarkReplacer = DarkReplacer(true)
private val ourLightReplacer = DarkReplacer(false)

// we need an object to propagate the replacer recursively to all parts of a compound icon
private class DarkReplacer(val dark: Boolean) : IconReplacer {
  override fun replaceIcon(icon: Icon): Icon {
    if (icon is DarkIconProvider) {
      return icon.getDarkIcon(dark)
    }
    return super.replaceIcon(icon)
  }
}