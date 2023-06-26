// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "DeprecatedCallableAddReplaceWith", "LiftReturnOrAssignment")

package com.intellij.openapi.util

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.ui.*
import com.intellij.ui.icons.*
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextSupport
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ReflectionUtil
import com.intellij.util.RetinaImage
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.ImageFilter
import java.awt.image.RGBImageFilter
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent
import kotlin.Pair

private val LOG: Logger
  get() = logger<IconLoader>()

private val iconCache = Caffeine.newBuilder()
  .expireAfterAccess(1, TimeUnit.HOURS)
  .maximumSize(256)
  .build<Pair<String, ClassLoader?>, CachedImageIcon>()

// contains mapping between icons and disabled icons
private val iconToDisabledIcon = ConcurrentHashMap<() -> RGBImageFilter, MutableMap<Icon, Icon>>()
private val standardDisablingFilter: () -> RGBImageFilter = { UIUtil.getGrayFilter() }

private val colorPatchCache = ConcurrentHashMap<Int, MutableMap<LongArray, MutableMap<Icon, Icon>>>()

@Volatile
private var STRICT_GLOBAL = false

internal val fakeComponent: JComponent by lazy { object : JComponent() {} }

/**
 * Provides access to icons used in the UI.
 * Please see [Working with Icons and Images](http://www.jetbrains.org/intellij/sdk/docs/reference_guide/work_with_icons_and_images.html)
 * about supported formats, organization, and accessing icons in plugins.
 *
 * @see com.intellij.util.IconUtil
 */
object IconLoader {
  fun setStrictGlobally(strict: Boolean) {
    STRICT_GLOBAL = strict
  }

  @JvmStatic
  fun installPathPatcher(patcher: IconPathPatcher) {
    updateTransform { it.withPathPatcher(patcher) }
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
  fun setFilter(filter: ImageFilter?) {
    updateTransform { it.withFilter(filter) }
  }

  @JvmStatic
  fun clearCache() {
    // copy the transform to trigger update of cached icons
    updateTransform(IconTransform::copy)
  }

  @TestOnly
  @JvmStatic
  fun clearCacheInTests() {
    iconCache.invalidateAll()
    iconToDisabledIcon.clear()
    clearImageCache()
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
    return findIcon(originalPath = path, aClass = aClass, classLoader = aClass.classLoader, deferUrlResolve = true)
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
    return findIconUsingNewImplementation(path, aClass.classLoader)
  }

  @JvmStatic
  fun findIcon(path: String, aClass: Class<*>, deferUrlResolve: Boolean, strict: Boolean): Icon? {
    return findIcon(originalPath = path, aClass = aClass, classLoader = aClass.classLoader, strict, deferUrlResolve = deferUrlResolve)
  }

  @JvmStatic
  @JvmOverloads
  fun findIcon(url: URL?, storeToCache: Boolean = true): Icon? {
    if (url == null) {
      return null
    }

    val key = Pair<String, ClassLoader?>(url.toString(), null)
    if (storeToCache) {
      return iconCache.get(key) { CachedImageIcon(url = url, useCacheOnLoad = true) }
    }
    else {
      return iconCache.getIfPresent(key) ?: CachedImageIcon(url = url, useCacheOnLoad = false)
    }
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

  @Internal
  fun getDisabledIcon(icon: Icon, disableFilter: (() -> RGBImageFilter)?): Icon {
    if (!isIconActivated) {
      return icon
    }

    val effectiveIcon = if (icon is LazyIcon) icon.getOrComputeIcon() else icon
    val filter = disableFilter ?: standardDisablingFilter /* returns laf-aware instance */
    return iconToDisabledIcon
      .computeIfAbsent(filter) { CollectionFactory.createConcurrentWeakKeyWeakValueMap() }
      .computeIfAbsent(effectiveIcon) { filterIcon(icon = it, filterSupplier = filter) }
  }

  /**
   * Creates a new icon with the color patching applied.
   */
  fun colorPatchedIcon(icon: Icon, colorPatcher: SvgElementColorPatcherProvider): Icon {
    return replaceCachedImageIcons(icon) { patchColorsInCacheImageIcon(imageIcon = it, colorPatcher = colorPatcher, isDark = null) }!!
  }

  @Internal
  fun patchColorsInCacheImageIcon(imageIcon: com.intellij.ui.icons.CachedImageIcon,
                                  colorPatcher: SvgElementColorPatcherProvider,
                                  isDark: Boolean?): Icon {
    var result = imageIcon
    if (isDark != null) {
      val variant = result.getDarkIcon(isDark)
      if (variant is com.intellij.ui.icons.CachedImageIcon) {
        result = variant
      }
    }

    var digest = colorPatcher.digest()
    if (digest == null) {
      @Suppress("DEPRECATION")
      val bytes = colorPatcher.wholeDigest()
      if (bytes != null) {
        digest = longArrayOf(hasher.hashBytesToLong(bytes), seededHasher.hashBytesToLong(bytes))
      }
    }

    if (digest == null) {
      return result.createWithPatcher(colorPatcher)
    }

    val topMapIndex = when(isDark) {
      false -> 0
      true -> 1
      else -> 2
    }

    return colorPatchCache.computeIfAbsent(topMapIndex) { CollectionFactory.createConcurrentWeakKeyWeakValueMap() }
      .computeIfAbsent(digest) { CollectionFactory.createConcurrentWeakKeyWeakValueMap() }
      .computeIfAbsent(imageIcon) {result.createWithPatcher(colorPatcher) }
  }

  /**
   * Creates a new icon with the filter applied.
   */
  fun filterIcon(icon: Icon, filterSupplier: () -> RGBImageFilter): Icon {
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


  fun getScaleToRenderIcon(icon: Icon, ancestor: Component?): Float {
    val ctxSupport = getScaleContextSupport(icon)
    val scale = if (ctxSupport == null) {
      (if (JreHiDpiUtil.isJreHiDPI(null as GraphicsConfiguration?)) sysScale(ancestor) else 1.0f)
    }
    else {
      if (JreHiDpiUtil.isJreHiDPI(null as GraphicsConfiguration?)) ctxSupport.getScale(ScaleType.SYS_SCALE).toFloat() else 1.0f
    }
    return scale
  }

  fun renderFilteredIcon(icon: Icon,
                         scale: Double,
                         filterSupplier: Supplier<out RGBImageFilter?>,
                         ancestor: Component?): JBImageIcon {
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage((scale * icon.iconWidth).toInt(), (scale * icon.iconHeight).toInt(), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.color = Gray.TRANSPARENT
    graphics.fillRect(0, 0, icon.iconWidth, icon.iconHeight)
    graphics.scale(scale, scale)
    // We want to paint here on the fake component:
    // painting on the real component will have other coordinates at least.
    // Also, it may be significant if the icon contains updatable icon (e.g. DeferredIcon), and it will schedule incorrect repaint
    icon.paintIcon(fakeComponent, graphics, 0, 0)
    graphics.dispose()
    var img = ImageUtil.filter(image, filterSupplier.get())
    if (StartupUiUtil.isJreHiDPI(ancestor)) {
      img = RetinaImage.createFrom(img!!, scale, null)
    }
    return JBImageIcon(img!!)
  }

  @JvmStatic
  fun getTransparentIcon(icon: Icon): Icon {
    return getTransparentIcon(icon, 0.5f)
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
    return object : IconReplacer {
      override fun replaceIcon(icon: Icon): Icon {
        if (icon is DarkIconProvider) return icon.getDarkIcon(dark)
        return super.replaceIcon(icon)
      }
    }.replaceIcon(icon)
  }

  fun detachClassLoader(classLoader: ClassLoader) {
    iconCache.asMap().entries.removeIf { (key, icon) ->
      icon.detachClassLoader(classLoader) || key.second === classLoader
    }
  }

  @JvmStatic
  fun createLazy(producer: Supplier<out Icon>): Icon {
    return LazyIcon(producer)
  }

  @Deprecated("Do not use")
  open class CachedImageIcon private constructor(
    originalPath: String?,
    resolver: ImageDataLoader?,
    isDarkOverridden: Boolean?,
    localFilterSupplier: (() -> RGBImageFilter)? = null,
    colorPatcher: SvgElementColorPatcherProvider? = null,
    useStroke: Boolean = false
  ) : com.intellij.ui.icons.CachedImageIcon(
    originalPath = originalPath,
    resolver = resolver,
    isDarkOverridden = isDarkOverridden,
    localFilterSupplier = localFilterSupplier,
    colorPatcher = colorPatcher,
    useStroke = useStroke,
  )
}

/**
 * Returns [ScaleContextSupport] which best represents this icon taking into account its compound structure, or null when not applicable.
 */
private fun getScaleContextSupport(icon: Icon): ScaleContextSupport? {
  return when (icon) {
    is ScaleContextSupport -> icon
    is RetrievableIcon -> getScaleContextSupport(icon.retrieveIcon())
    is CompositeIcon -> {
      if (icon.iconCount == 0) {
        return null
      }
      getScaleContextSupport(icon.getIcon(0) ?: return null)
    }
    else -> null
  }
}

private fun updateTransform(updater: Function<in IconTransform, IconTransform>) {
  var prev: IconTransform
  var next: IconTransform
  do {
    prev = pathTransform.get()
    next = updater.apply(prev)
  }
  while (!pathTransform.compareAndSet(prev, next))
  pathTransformGlobalModCount.incrementAndGet()
  if (prev != next) {
    iconToDisabledIcon.clear()
    colorPatchCache.clear()
    iconToStrokeIcon.clear()

    // clear svg cache
    clearImageCache()
    // iconCache is not cleared because it contains an original icon (instance that will delegate to)
  }
}

private fun findIcon(originalPath: String,
                     aClass: Class<*>?,
                     classLoader: ClassLoader,
                     strict: Boolean = STRICT_GLOBAL,
                     deferUrlResolve: Boolean): Icon? {
  if (deferUrlResolve) {
    return findIconUsingDeprecatedImplementation(originalPath = originalPath,
                                                 classLoader = classLoader,
                                                 aClass = aClass,
                                                 strict = strict)
  }
  else {
    return findIconUsingNewImplementation(originalPath, classLoader)
  }
}

@Internal
fun findIconUsingNewImplementation(path: String, classLoader: ClassLoader, toolTip: Supplier<String?>? = null): Icon? {
  return ImageDataByPathLoader.findIconByPath(path = path, classLoader = classLoader, cache = iconCache.asMap(), toolTip = toolTip)
}

@Internal
fun findIconUsingDeprecatedImplementation(originalPath: String,
                                          classLoader: ClassLoader,
                                          aClass: Class<*>?,
                                          toolTip: Supplier<String?>? = null,
                                          strict: Boolean = STRICT_GLOBAL): Icon? {
  var effectiveClassLoader = classLoader
  val startTime = StartUpMeasurer.getCurrentTimeIfEnabled()
  val patchedPath = patchIconPath(originalPath = originalPath, classLoader = effectiveClassLoader)
  val effectivePath = patchedPath?.first ?: originalPath
  if (patchedPath?.second != null) {
    effectiveClassLoader = patchedPath.second
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
        val resolver = ImageDataByPathResourceLoader(path = effectivePath, ownerClass = aClass, classLoader = k.second, strict = strict)
        CachedImageIcon(originalPath = k.first, resolver = resolver, toolTip = toolTip)
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