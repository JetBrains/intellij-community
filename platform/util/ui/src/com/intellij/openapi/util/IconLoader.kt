// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "DeprecatedCallableAddReplaceWith")

package com.intellij.openapi.util

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.ImageDataByPathLoader.Companion.findIcon
import com.intellij.openapi.util.CachedImageIcon.Companion.pathTransform
import com.intellij.openapi.util.CachedImageIcon.Companion.pathTransformGlobalModCount
import com.intellij.openapi.util.IconLoader.getReflectiveIcon
import com.intellij.openapi.util.IconLoader.isReflectivePath
import com.intellij.openapi.util.IconLoader.patchPath
import com.intellij.ui.*
import com.intellij.ui.icons.*
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextSupport
import com.intellij.ui.scale.ScaleType
import com.intellij.util.*
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.xxh3.Xxh3
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.ImageFilter
import java.awt.image.RGBImageFilter
import java.lang.invoke.MethodHandles
import java.lang.ref.WeakReference
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent

private val LOG: Logger
  get() = logger<IconLoader>()

private val LOOKUP = MethodHandles.lookup()

// the key: Pair(path, classLoader)
private val iconCache = ConcurrentHashMap<Pair<String, ClassLoader?>, CachedImageIcon>(100, 0.9f, 2)

// contains mapping between icons and disabled icons
private val iconToDisabledIcon = ConcurrentHashMap<() -> RGBImageFilter, MutableMap<Icon, Icon>>()
private val standardDisablingFilter: () -> RGBImageFilter = { UIUtil.getGrayFilter() }

private val colorPatchCache = ConcurrentHashMap<Int, MutableMap<LongArray, MutableMap<Icon, Icon>>>()

@Volatile
private var STRICT_GLOBAL = false

private val STRICT_LOCAL: ThreadLocal<Boolean> = object : ThreadLocal<Boolean>() {
  override fun initialValue(): Boolean = false

  override fun get(): Boolean = STRICT_GLOBAL || super.get()
}

internal val fakeComponent: JComponent by lazy { object : JComponent() {} }

/**
 * Provides access to icons used in the UI.
 * Please see [Working with Icons and Images](http://www.jetbrains.org/intellij/sdk/docs/reference_guide/work_with_icons_and_images.html)
 * about supported formats, organization, and accessing icons in plugins.
 *
 * @see com.intellij.util.IconUtil
 */
object IconLoader {
  @ApiStatus.Internal
  fun loadCustomVersion(icon: com.intellij.openapi.util.CachedImageIcon, width: Int, height: Int): Icon? {
    val resolver = icon.resolver
    val url = resolver?.url
    val path = url?.toString()
    if (path != null && path.endsWith(".svg")) {
      val modified = "${path.substring(0, path.length - 4)}@${width}x$height.svg"
      try {
        val foundIcon = findIcon(URL(modified))
        if (foundIcon is com.intellij.openapi.util.CachedImageIcon && foundIcon.getIconWidth() == scale(width) && foundIcon.getIconHeight() == scale(height)) {
          return foundIcon
        }
      }
      catch (ignore: MalformedURLException) {
      }
    }
    return null
  }

  /** @param size the size before system scaling (without JBUIScale.scale)
   */
  @ApiStatus.Internal
  fun loadCustomVersionOrScale(icon: ScalableIcon, size: Int): Icon {
    if (icon.iconWidth == scale(size)) {
      return icon
    }
    var cachedIcon: Icon = icon
    if (cachedIcon !is com.intellij.openapi.util.CachedImageIcon && cachedIcon is RetrievableIcon) {
      cachedIcon = cachedIcon.retrieveIcon()
    }
    if (cachedIcon is com.intellij.openapi.util.CachedImageIcon) {
      val version = loadCustomVersion(cachedIcon, size, size)
      if (version != null) {
        return version
      }
    }
    return icon.scale(scale(1.0f) * size / icon.iconWidth)
  }

  @TestOnly
  @JvmStatic
  fun <T> performStrictly(computable: Supplier<out T>): T {
    STRICT_LOCAL.set(true)
    return try {
      computable.get()
    }
    finally {
      STRICT_LOCAL.set(false)
    }
  }

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
    iconCache.clear()
    iconToDisabledIcon.clear()
    ImageCache.INSTANCE.clearCache()
    pathTransformGlobalModCount.incrementAndGet()
  }

  @Deprecated("Use {@link #getIcon(String, ClassLoader)}", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun getIcon(path: @NonNls String): Icon {
    return getIcon(path = path, aClass = ReflectionUtil.getGrandCallerClass() ?: error(path))
  }

  @JvmStatic
  fun getReflectiveIcon(path: String, classLoader: ClassLoader): Icon? {
    return try {
      var dotIndex = path.lastIndexOf('.')
      val fieldName = path.substring(dotIndex + 1)
      val builder = StringBuilder(path.length + 20)
      builder.append(path, 0, dotIndex)
      var separatorIndex = -1
      do {
        dotIndex = path.lastIndexOf('.', dotIndex - 1)
        // if starts with a lower case, char - it is a package name
        if (dotIndex == -1 || Character.isLowerCase(path[dotIndex + 1])) {
          break
        }
        if (separatorIndex != -1) {
          builder.setCharAt(separatorIndex, '$')
        }
        separatorIndex = dotIndex
      }
      while (true)
      if (!Character.isLowerCase(builder[0])) {
        if (separatorIndex != -1) {
          builder.setCharAt(separatorIndex, '$')
        }
        builder.insert(0, if (path.startsWith("AllIcons.")) "com.intellij.icons." else "icons.")
      }
      val aClass = classLoader.loadClass(builder.toString())
      LOOKUP.findStaticGetter(aClass, fieldName, Icon::class.java).invoke() as Icon
    }
    catch (e: Throwable) {
      LOG.warn("Cannot get reflective icon (path=$path)", e)
      null
    }
  }

  @Deprecated("Use {@link #findIcon(String, ClassLoader)}.", level = DeprecationLevel.ERROR)
  @JvmStatic
  fun findIcon(path: @NonNls String): Icon? {
    val callerClass = ReflectionUtil.getGrandCallerClass() ?: return null
    return findIcon(path = path, classLoader = callerClass.classLoader)
  }

  @JvmStatic
  fun getIcon(path: String, aClass: Class<*>): Icon {
    return findIcon(originalPath = path, aClass = aClass, classLoader = aClass.classLoader, handleNotFound = null, deferUrlResolve = true)
           ?: throw IllegalStateException("Icon cannot be found in '" + path + "', class='" + aClass.name + "'")
  }

  @JvmStatic
  fun getIcon(path: String, classLoader: ClassLoader): Icon {
    return findIcon(path = path, classLoader = classLoader)
           ?: throw IllegalStateException("Icon cannot be found in '$path', classLoader='$classLoader'")
  }

  fun createNewResolverIfNeeded(originalClassLoader: ClassLoader?,
                                originalPath: String,
                                transform: IconTransform): ImageDataLoader? {
    val patchedPath = transform.patchPath(originalPath, originalClassLoader) ?: return null
    val classLoader = if (patchedPath.second == null) originalClassLoader else patchedPath.second
    val path = patchedPath.first
    if (path != null && path.startsWith("/")) {
      return FinalImageDataLoader(path = path.substring(1), classLoader = classLoader ?: transform.javaClass.classLoader)
    }

    // This uses case for temp themes only. Here we want to immediately replace the existing icon with a local one
    if (path != null && path.startsWith("file:/")) {
      try {
        val resolver = ImageDataByUrlLoader(URL(path), path, classLoader, false)
        resolver.resolve()
        return resolver
      }
      catch (ignore: MalformedURLException) {
      }
    }
    return null
  }

  @TestOnly
  @JvmStatic
  fun activate() {
    com.intellij.openapi.util.CachedImageIcon.isActivated = true
  }

  @TestOnly
  @JvmStatic
  fun deactivate() {
    com.intellij.openapi.util.CachedImageIcon.isActivated = false
  }

  /**
   * Might return null if the icon was not found.
   * Use only if you expected null return value, otherwise see [IconLoader.getIcon]
   */
  @JvmStatic
  fun findIcon(path: String, aClass: Class<*>): Icon? {
    return findIcon(originalPath = path, originalClassLoader = aClass.classLoader, cache = iconCache)
  }

  @JvmStatic
  fun findIcon(path: String, aClass: Class<*>, deferUrlResolve: Boolean, strict: Boolean): Icon? {
    return findIcon(originalPath = path,
                    aClass = aClass,
                    classLoader = aClass.classLoader,
                    handleNotFound = if (strict) HandleNotFound.THROW_EXCEPTION else HandleNotFound.IGNORE,
                    deferUrlResolve = deferUrlResolve)
  }

  fun isReflectivePath(path: String): Boolean {
    return !path.isEmpty() && path[0] != '/' && path.contains("Icons.")
  }

  @JvmStatic
  @JvmOverloads
  fun findIcon(url: URL?, storeToCache: Boolean = true): Icon? {
    if (url == null) {
      return null
    }

    val key = Pair<String, ClassLoader?>(url.toString(), null)
    return if (storeToCache) {
      iconCache.computeIfAbsent(key) { CachedImageIcon(url = url, useCacheOnLoad = true) }
    }
    else {
      iconCache.get(key) ?: CachedImageIcon(url = url, useCacheOnLoad = false)
    }
  }

  @JvmStatic
  fun patchPath(originalPath: String, classLoader: ClassLoader): kotlin.Pair<String, ClassLoader>? {
    return pathTransform.get().patchPath(originalPath, classLoader)
  }

  @JvmStatic
  fun findIcon(path: String, classLoader: ClassLoader): Icon? {
    return findIcon(originalPath = path, originalClassLoader = classLoader, cache = iconCache)
  }

  @JvmStatic
  fun findResolvedIcon(path: String, classLoader: ClassLoader): Icon? {
    val icon = findIcon(originalPath = path, originalClassLoader = classLoader, cache = iconCache)
    return if (icon is com.intellij.openapi.util.CachedImageIcon && icon.getRealIcon() === com.intellij.openapi.util.CachedImageIcon.EMPTY_ICON) null else icon
  }

  @JvmOverloads
  @JvmStatic
  fun toImage(icon: Icon, ctx: ScaleContext? = null): Image? {
    var effectiveIcon = icon
    var effectiveScaleContext = ctx
    if (effectiveIcon is RetrievableIcon) {
      effectiveIcon = getOrigin(effectiveIcon)
    }
    if (effectiveIcon is com.intellij.openapi.util.CachedImageIcon) {
      effectiveIcon = effectiveIcon.getRealIcon(effectiveScaleContext)
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
      image = ImageUtil.createImage(effectiveScaleContext, effectiveIcon.iconWidth.toDouble(), effectiveIcon.iconHeight.toDouble(),
                                    BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.ROUND)
    }
    else {
      if (effectiveScaleContext == null) {
        effectiveScaleContext = ScaleContext.create()
      }
      image = if (StartupUiUtil.isJreHiDPI(effectiveScaleContext)) {
        JBHiDPIScaledImage(effectiveScaleContext, effectiveIcon.iconWidth.toDouble(), effectiveIcon.iconHeight.toDouble(),
                           BufferedImage.TYPE_INT_ARGB_PRE,
                           PaintUtil.RoundingMode.ROUND)
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

  fun copy(icon: Icon, ancestor: Component?, deepCopy: Boolean): Icon {
    if (icon is CopyableIcon) {
      return if (deepCopy) icon.deepCopy() else icon.copy()
    }

    val image = ImageUtil.createImage(ancestor?.graphicsConfiguration, icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      icon.paintIcon(ancestor, g, 0, 0)
    }
    finally {
      g.dispose()
    }

    return object : JBImageIcon(image) {
      val originalWidth = icon.iconWidth
      val originalHeight = icon.iconHeight
      override fun getIconWidth(): Int = originalWidth

      override fun getIconHeight(): Int = originalHeight
    }
  }

  @JvmStatic
  fun isGoodSize(icon: Icon): Boolean {
    return icon.iconWidth > 0 && icon.iconHeight > 0
  }

  /**
   * Gets (creates if necessary) disabled icon based on the passed one.
   *
   * @return `ImageIcon` constructed from disabled image of passed icon.
   */
  @JvmStatic
  fun getDisabledIcon(icon: Icon): Icon {
    return getDisabledIcon(icon = icon, disableFilter = null)
  }

  @ApiStatus.Internal
  fun getDisabledIcon(icon: Icon, disableFilter: (() -> RGBImageFilter)?): Icon {
    if (!com.intellij.openapi.util.CachedImageIcon.isActivated) {
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

  @ApiStatus.Internal
  fun patchColorsInCacheImageIcon(imageIcon: com.intellij.openapi.util.CachedImageIcon, colorPatcher: SvgElementColorPatcherProvider, isDark: Boolean?): Icon {
    var result = imageIcon
    if (isDark != null) {
      val variant = result.getDarkIcon(isDark)
      if (variant is com.intellij.openapi.util.CachedImageIcon) {
        result = variant
      }
    }

    var digest = colorPatcher.digest()
    if (digest == null) {
      @Suppress("DEPRECATION")
      val bytes = colorPatcher.wholeDigest()
      if (bytes != null) {
        digest = longArrayOf(Xxh3.hash(bytes), Xxh3.seededHash(bytes, 5238470482016868669L))
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
   * Creates a new icon with the low-level CachedImageIcon changing
   */
  @ApiStatus.Internal
  fun replaceCachedImageIcons(icon: Icon, cachedImageIconReplacer: (com.intellij.openapi.util.CachedImageIcon) -> Icon): Icon? {
    val replacer: IconReplacer = object : IconReplacer {
      override fun replaceIcon(icon: Icon?): Icon? {
        return when {
          icon == null || icon is DummyIcon || icon is EmptyIcon -> icon
          icon is LazyIcon -> replaceIcon(icon.getOrComputeIcon())
          icon is ReplaceableIcon -> icon.replaceBy(this)
          !checkIconSize(icon) -> {
            com.intellij.openapi.util.CachedImageIcon.EMPTY_ICON
          }
          icon is com.intellij.openapi.util.CachedImageIcon -> cachedImageIconReplacer(icon)
          else -> icon
        }
      }
    }
    return replacer.replaceIcon(icon)
  }

  /**
   * Creates a new icon with the filter applied.
   */
  fun filterIcon(icon: Icon, filterSupplier: () -> RGBImageFilter): Icon {
    val effectiveIcon = if (icon is LazyIcon) icon.getOrComputeIcon() else icon
    if (!checkIconSize(effectiveIcon)) {
      return com.intellij.openapi.util.CachedImageIcon.EMPTY_ICON
    }
    return if (effectiveIcon is com.intellij.openapi.util.CachedImageIcon) {
      effectiveIcon.createWithFilter(filterSupplier)
    }
    else {
      FilteredIcon(effectiveIcon, filterSupplier)
    }
  }

  private fun checkIconSize(icon: Icon): Boolean {
    if (!isGoodSize(icon)) {
      LOG.error("Icon $icon has incorrect size: ${icon.iconWidth}x${icon.iconHeight}")
      return false
    }
    return true
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

  /**
   * Gets a snapshot of the icon, immune to changes made by these calls:
   * [.setFilter], [.setUseDarkIcons]
   *
   * @param icon the source icon
   * @return the icon snapshot
   */
  @JvmStatic
  fun getIconSnapshot(icon: Icon): Icon {
    return if (icon is com.intellij.openapi.util.CachedImageIcon) {
      icon.getRealIcon()
    }
    else icon
  }

  /**
   * For internal usage. Converts the icon to 1x scale when applicable.
   */
  @ApiStatus.Internal
  fun getMenuBarIcon(icon: Icon, dark: Boolean): Icon {
    var effectiveIcon = icon
    if (effectiveIcon is RetrievableIcon) {
      effectiveIcon = getOrigin(effectiveIcon)
    }
    return if (effectiveIcon is MenuBarIconProvider) effectiveIcon.getMenuBarIcon(dark) else effectiveIcon
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
    iconCache.entries.removeIf { (key, icon): Map.Entry<Pair<String, ClassLoader?>, com.intellij.openapi.util.CachedImageIcon> ->
      icon.detachClassLoader(classLoader) || key.second === classLoader
    }
  }

  internal fun doResolve(path: String?,
                         classLoader: ClassLoader?,
                         ownerClass: Class<*>?,
                         handleNotFound: HandleNotFound): URL? {
    var effectivePath = path
    var url: URL? = null
    if (effectivePath != null) {
      if (classLoader != null) {
        // paths in ClassLoader getResource must not start with "/"
        effectivePath = if (effectivePath[0] == '/') effectivePath.substring(1) else effectivePath
        url = findUrl(path = effectivePath, urlProvider = classLoader::getResource)
      }
      if (url == null && ownerClass != null) {
        // some plugins use findIcon("icon.png",IconContainer.class)
        url = findUrl(path = effectivePath, urlProvider = ownerClass::getResource)
      }
    }
    if (url == null) {
      handleNotFound.handle("Can't find icon in '$effectivePath' near $classLoader")
    }
    return url
  }

  @JvmStatic
  fun createLazy(producer: Supplier<out Icon>): Icon {
    return object : LazyIcon() {
      override fun replaceBy(replacer: IconReplacer): Icon {
        return createLazy { replacer.replaceIcon(producer.get()) }
      }

      override fun compute(): Icon = producer.get()
    }
  }

  @Deprecated("Do not use")
  open class CachedImageIcon private constructor(
     originalPath: String?,
      resolver: ImageDataLoader?,
     isDarkOverridden: Boolean?,
     localFilterSupplier: (() -> RGBImageFilter)? = null,
     colorPatcher: SvgElementColorPatcherProvider? = null,
     useStroke: Boolean = false
  ) : com.intellij.openapi.util.CachedImageIcon(
    originalPath = originalPath,
    resolver = resolver,
    isDarkOverridden = isDarkOverridden,
    localFilterSupplier = localFilterSupplier,
    colorPatcher = colorPatcher,
    useStroke = useStroke,
  )
}

private fun getOrigin(icon: RetrievableIcon): Icon {
  val maxDeep = 10
  var origin = icon.retrieveIcon()
  var level = 0
  while (origin is RetrievableIcon && level < maxDeep) {
    ++level
    origin = origin.retrieveIcon()
  }
  if (origin is RetrievableIcon) {
    LOG.error("can't calculate origin icon (too deep in hierarchy), src: $icon")
  }
  return origin
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
    CachedImageIcon.iconToStrokeIcon.clear()

    // clear svg cache
    ImageCache.INSTANCE.clearCache()
    // iconCache is not cleared because it contains an original icon (instance that will delegate to)
  }
}

private fun findIcon(originalPath: String,
                     aClass: Class<*>?,
                     classLoader: ClassLoader,
                     handleNotFound: HandleNotFound?,
                     deferUrlResolve: Boolean): Icon? {
  var effectiveClassLoader = classLoader
  if (!deferUrlResolve) {
    return findIcon(originalPath = originalPath, originalClassLoader = effectiveClassLoader, cache = iconCache)
  }

  val startTime = StartUpMeasurer.getCurrentTimeIfEnabled()
  val patchedPath = patchPath(originalPath = originalPath, classLoader = effectiveClassLoader)
  val path = patchedPath?.first ?: originalPath
  if (patchedPath?.second != null) {
    effectiveClassLoader = patchedPath.second
  }

  val icon: Icon?
  if (isReflectivePath(path)) {
    icon = getReflectiveIcon(path = path, classLoader = effectiveClassLoader)
  }
  else {
    val key = Pair(originalPath, effectiveClassLoader)
    var cachedIcon = iconCache.get(key)
    if (cachedIcon == null) {
      cachedIcon = iconCache.computeIfAbsent(key) { k ->
        val classLoader1 = k.getSecond()
        val effectiveHandleNotFound = handleNotFound
                                      ?: if (STRICT_LOCAL.get()) HandleNotFound.THROW_EXCEPTION else HandleNotFound.IGNORE
        val resolver = ImageDataByUrlLoader(path, aClass, classLoader1, effectiveHandleNotFound,  /* useCacheOnLoad = */true)
        CachedImageIcon(originalPath = originalPath, resolver = resolver)
      }
    }
    else {
      val scaleContext = ScaleContext.create()
      if (cachedIcon.scaleContext != scaleContext) {
        // honor scale context as 'iconCache' doesn't do that
        cachedIcon = cachedIcon.copy()
        cachedIcon.updateScaleContext(scaleContext)
      }
    }
    icon = cachedIcon
  }
  if (startTime != -1L) {
    IconLoadMeasurer.findIcon.end(startTime)
  }
  return icon
}

internal enum class HandleNotFound {
  THROW_EXCEPTION {
    override fun handle(msg: String) {
      throw RuntimeException(msg)
    }
  },
  IGNORE;

  open fun handle(msg: String) {
  }
}

private abstract class LazyIcon : ScaleContextSupport(), CopyableIcon, RetrievableIcon {
  private var wasComputed = false

  @Volatile
  private var icon: Icon? = null

  private var transformModCount = pathTransformGlobalModCount.get()

  override fun isComplex(): Boolean = true

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    if (updateScaleContext(ScaleContext.create(g as Graphics2D))) {
      icon = null
    }

    getOrComputeIcon().paintIcon(c, g, x, y)
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
      compute()
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

  protected abstract fun compute(): Icon

  override fun retrieveIcon(): Icon = getOrComputeIcon()

  override fun copy(): Icon = IconLoader.copy(icon = getOrComputeIcon(), ancestor = null, deepCopy = false)
}

private fun findUrl(path: String, urlProvider: (String) -> URL?): URL? {
  urlProvider(path)?.let {
    return it
  }

  // Find either PNG or SVG icon.
  // The icon will then be wrapped into CachedImageIcon,
  // which will load a proper icon version depending on the context - UI theme, DPI.
  // SVG version, when present, has more priority than PNG.
  // See for details: com.intellij.util.ImageLoader.ImageDescList#create
  var effectivePath = path
  when {
    effectivePath.endsWith(".png") -> effectivePath = effectivePath.substring(0, effectivePath.length - 4) + ".svg"
    effectivePath.endsWith(".svg") -> effectivePath = effectivePath.substring(0, effectivePath.length - 4) + ".png"
    else -> LOG.debug("unexpected path: ", effectivePath)
  }
  return urlProvider(effectivePath)
}

private class FinalImageDataLoader(private val path: String, classLoader: ClassLoader) : ImageDataLoader {
  private val classLoaderRef: WeakReference<ClassLoader>

  init {
    classLoaderRef = WeakReference(classLoader)
  }

  override fun loadImage(parameters: LoadIconParameters): Image? {
    // do not use cache
    var flags = ImageLoader.ALLOW_FLOAT_SCALING
    if (parameters.isDark) {
      flags = flags or ImageLoader.USE_DARK
    }

    val classLoader = classLoaderRef.get() ?: return null
    return loadImage(path = path,
                     parameters = parameters,
                     resourceClass = null,
                     classLoader = classLoader,
                     flags = flags,
                     isUpScaleNeeded = !path.endsWith(".svg"))
  }

  override val url: URL?
    get() = classLoaderRef.get()?.getResource(path)

  // this resolver is already produced as a result of a patch
  override fun patch(originalPath: String, transform: IconTransform): ImageDataLoader? = null

  override fun isMyClassLoader(classLoader: ClassLoader): Boolean = classLoaderRef.get() === classLoader

  override fun toString(): String = "FinalImageDataLoader(classLoader=${classLoaderRef.get()}, path='$path')"
}