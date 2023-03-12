// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.reference.SoftReference
import com.intellij.ui.scale.AbstractScaleContextAware
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.SVGLoader
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.MultiResolutionImageProvider
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.ImageFilter
import java.awt.image.RGBImageFilter
import java.lang.ref.Reference
import java.net.URL
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.ImageIcon

@ApiStatus.Internal
@ApiStatus.NonExtendable
open class CachedImageIcon protected constructor(
  val originalPath: String?,
  @field:Volatile var resolver: ImageDataLoader?,
  private val isDarkOverridden: Boolean?,
  private val localFilterSupplier: (() -> RGBImageFilter)? = null,
  private val colorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null,
  private val useStroke: Boolean = false,
  private val toolTip: Supplier<String?>? = null,
) : AbstractScaleContextAware<ScaleContext>(ScaleContext.create()), CopyableIcon, ScalableIcon, DarkIconProvider, MenuBarIconProvider,
    IconWithToolTip {
  companion object {
    @JvmField
    internal var isActivated: Boolean = !GraphicsEnvironment.isHeadless()

    @JvmField
    internal val pathTransformGlobalModCount: AtomicInteger = AtomicInteger()

    fun patchPath(originalPath: String, classLoader: ClassLoader): Pair<String, ClassLoader>? {
      return pathTransform.get().patchPath(originalPath, classLoader)
    }

    @JvmField
    internal val pathTransform: AtomicReference<IconTransform> = AtomicReference(
      IconTransform(StartupUiUtil.isUnderDarcula(), arrayOf<IconPathPatcher>(DeprecatedDuplicatesIconPathPatcher()), null)
    )

    @JvmField
    internal val iconToStrokeIcon: ConcurrentMap<CachedImageIcon, CachedImageIcon> =
      CollectionFactory.createConcurrentWeakKeyWeakValueMap<CachedImageIcon, CachedImageIcon>()

    @Suppress("UndesirableClassUsage")
    val EMPTY_ICON: ImageIcon = object : ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
      override fun toString(): String = "Empty icon ${super.toString()}"
    }
  }

  private val originalResolver = resolver
  private var pathTransformModCount = -1

  private val scaledIconCache = ScaledIconCache()

  @Volatile
  private var darkVariant: CachedImageIcon? = null
  private val lock = Any()

  // ImageIcon (if small icon) or SoftReference<ImageIcon> (if large icon)
  @Volatile
  private var realIcon: Any? = null

  constructor(url: URL, useCacheOnLoad: Boolean) : this(originalPath = null,
                                                        resolver = ImageDataByUrlLoader(url = url,
                                                                                        classLoader = null,
                                                                                        useCacheOnLoad = useCacheOnLoad),
                                                        isDarkOverridden = null,
                                                        colorPatcher = null) {

    // if url is explicitly specified, it means that path should be not transformed
    pathTransformModCount = pathTransformGlobalModCount.get()
  }

  constructor(originalPath: String?, resolver: ImageDataLoader?, toolTip: Supplier<String?>?) :
    this(originalPath = originalPath, resolver = resolver, isDarkOverridden = null, colorPatcher = null, toolTip = toolTip)

  init {
    // for instance, ShadowPainter updates the context from and outside
    scaleContext.addUpdateListener {
      realIcon = null
    }
  }

  override fun getToolTip(composite: Boolean): String? = toolTip?.get()

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    getRealIcon(ScaleContext.create(if (g is Graphics2D) g else null)).paintIcon(c, g, x, y)
  }

  override fun getIconWidth(): Int = getRealIcon(scaleContext = null).iconWidth

  override fun getIconHeight(): Int = getRealIcon(scaleContext = null).iconHeight

  override fun getScale(): Float = 1.0f

  @ApiStatus.Internal
  fun getRealIcon(): ImageIcon = getRealIcon(scaleContext = null)

  internal fun getRealIcon(scaleContext: ScaleContext?): ImageIcon {
    if (resolver == null || !isActivated) {
      return EMPTY_ICON
    }

    var realIcon: Any?
    if (pathTransformGlobalModCount.get() == pathTransformModCount) {
      realIcon = this.realIcon
    }
    else {
      synchronized(lock) {
        this.resolver = originalResolver
        val resolver = this.resolver ?: return EMPTY_ICON
        if (pathTransformGlobalModCount.get() == pathTransformModCount) {
          realIcon = this.realIcon
        }
        else {
          pathTransformModCount = pathTransformGlobalModCount.get()
          realIcon = null
          this.realIcon = null
          scaledIconCache.clear()
          if (originalPath != null) {
            resolver.patch(originalPath = originalPath, transform = pathTransform.get())?.let {
              this.resolver = it
            }
          }
        }
      }
    }

    synchronized(lock) {
      // try returning the current icon as the context is up-to-date
      if (!updateScaleContext(scaleContext) && realIcon != null) {
        unwrapIcon(realIcon)?.let {
          return it
        }
      }

      scaledIconCache.getOrScaleIcon(scale = 1.0f, host = this, scaleContext = this.scaleContext)?.let { icon ->
        this.realIcon = if (icon.iconWidth < 50 && icon.iconHeight < 50) icon else SoftReference(icon)
        return icon
      }
    }
    return EMPTY_ICON
  }

  override fun toString(): String = resolver?.toString() ?: (originalPath ?: "unknown path")

  override fun scale(scale: Float): Icon {
    if (scale == 1.0f) {
      return this
    }
    else {
      val effectiveScaleContext: ScaleContext = scaleContext.copy()
      effectiveScaleContext.setScale(ScaleType.OBJ_SCALE.of(scale.toDouble()))
      return scaledIconCache.getOrScaleIcon(scale = scale, host = this, scaleContext = effectiveScaleContext) ?: this
    }
  }

  fun scale(scale: Float, ancestor: Component?): Icon {
    if (scale == 1.0f && ancestor == null) {
      return this
    }

    val scaleContext = if (ancestor == null) ScaleContext.create(scaleContext) else ScaleContext.create(ancestor)
    scaleContext.setScale(ScaleType.OBJ_SCALE.of(scale.toDouble()))
    return scaledIconCache.getOrScaleIcon(scale = scale, host = this, scaleContext = scaleContext) ?: this
  }

  override fun getDarkIcon(isDark: Boolean): Icon {
    var result = if (isDark) darkVariant else null
    if (result == null) {
      synchronized(lock) {
        if (isDark) {
          result = darkVariant
        }

        if (result == null) {
          result = CachedImageIcon(originalPath = originalPath,
                                   resolver = resolver ?: return EMPTY_ICON,
                                   isDarkOverridden = isDark,
                                   localFilterSupplier = localFilterSupplier,
                                   colorPatcher = colorPatcher,
                                   useStroke = useStroke)
          if (isDark) {
            darkVariant = result
          }
        }
      }
    }
    return result!!
  }

  override fun getMenuBarIcon(isDark: Boolean): Icon {
    val useMRI = SystemInfoRt.isMac
    val scaleContext = if (useMRI) ScaleContext.create() else ScaleContext.createIdentity()
    scaleContext.setScale(ScaleType.USR_SCALE.of(1.0))
    var img = loadImage(scaleContext = scaleContext, isDark = isDark)
    if (useMRI) {
      img = MultiResolutionImageProvider.convertFromJBImage(img)
    }
    return ImageIcon(img ?: return this)
  }

  override fun copy(): CachedImageIcon {
    val result = CachedImageIcon(originalPath = originalPath,
                                 resolver = resolver,
                                 isDarkOverridden = isDarkOverridden,
                                 localFilterSupplier = localFilterSupplier,
                                 colorPatcher = colorPatcher,
                                 useStroke = useStroke)
    result.pathTransformModCount = pathTransformModCount
    return result
  }

  internal fun createWithFilter(filterSupplier: () -> RGBImageFilter): Icon {
    val resolver = resolver ?: return EMPTY_ICON
    return CachedImageIcon(originalPath = originalPath,
                           resolver = resolver,
                           isDarkOverridden = isDarkOverridden,
                           localFilterSupplier = filterSupplier,
                           colorPatcher = colorPatcher,
                           useStroke = useStroke)
  }

  internal fun createWithPatcher(colorPatcher: SVGLoader.SvgElementColorPatcherProvider): Icon {
    val resolver = resolver ?: return EMPTY_ICON
    return CachedImageIcon(originalPath = originalPath,
                           resolver = resolver,
                           isDarkOverridden = isDarkOverridden,
                           localFilterSupplier = localFilterSupplier,
                           colorPatcher = colorPatcher,
                           useStroke = useStroke)
  }

  fun createStrokeIcon(): Icon {
    val resolver = resolver ?: return EMPTY_ICON
    return iconToStrokeIcon.computeIfAbsent(this) {
      CachedImageIcon(originalPath = originalPath,
                      resolver = resolver,
                      isDarkOverridden = isDarkOverridden,
                      localFilterSupplier = localFilterSupplier,
                      colorPatcher = colorPatcher,
                      useStroke = true)
    }
  }

  val isDark: Boolean
    get() = isDarkOverridden ?: pathTransform.get().isDark

  private fun getFilters(): List<ImageFilter> {
    val global = pathTransform.get().filter
    val local = localFilterSupplier?.invoke()
    return if (global != null && local != null) listOf(global, local) else listOfNotNull(global ?: local)
  }

  val url: URL?
    get() = this.resolver?.url

  internal fun loadImage(scaleContext: ScaleContext, isDark: Boolean): Image? {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val resolver = resolver ?: return null
    val image = resolver.loadImage(parameters = LoadIconParameters(filters = getFilters(),
                                                                   isDark = isDark,
                                                                   colorPatcher = colorPatcher ?: SVGLoader.colorPatcherProvider,
                                                                   isStroke = useStroke),
                                   scaleContext = scaleContext)
    if (start != -1L) {
      IconLoadMeasurer.findIconLoad.end(start)
    }
    return image
  }

  internal fun detachClassLoader(loader: ClassLoader): Boolean {
    if (resolver == null) {
      return true
    }

    synchronized(lock) {
      val resolver = this.resolver ?: return true
      if (!resolver.isMyClassLoader(loader)) {
        return false
      }

      this.resolver = null
      scaledIconCache.clear()
      val darkVariant = darkVariant
      if (darkVariant != null) {
        this.darkVariant = null
        darkVariant.detachClassLoader(loader)
      }
      return true
    }
  }

  val imageFlags: Int
    get() {
      return (this.resolver ?: return 0).flags
    }
}

private fun unwrapIcon(icon: Any?): ImageIcon? {
  return when (icon) {
    null -> null
    is Reference<*> -> icon.get() as ImageIcon?
    else -> icon as ImageIcon?
  }
}