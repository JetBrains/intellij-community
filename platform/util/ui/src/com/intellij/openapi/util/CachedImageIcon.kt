// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.reference.SoftReference
import com.intellij.ui.icons.*
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextSupport
import com.intellij.ui.scale.ScaleType
import com.intellij.util.SVGLoader
import com.intellij.util.ui.MultiResolutionImageProvider
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.ImageFilter
import java.awt.image.RGBImageFilter
import java.lang.ref.Reference
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
) : ScaleContextSupport(), CopyableIcon, ScalableIcon, DarkIconProvider, MenuBarIconProvider {
  companion object {
    @JvmField
    internal var isActivated = !GraphicsEnvironment.isHeadless()

    @JvmField
    internal val pathTransformGlobalModCount = AtomicInteger()

    @JvmField
    internal val pathTransform = AtomicReference(
      IconTransform(StartupUiUtil.isUnderDarcula(), arrayOf<IconPathPatcher>(DeprecatedDuplicatesIconPathPatcher()), null)
    )

    @Suppress("UndesirableClassUsage")
    val EMPTY_ICON: ImageIcon = object : ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
      override fun toString(): String = "Empty icon ${super.toString()}"
    }

    private fun unwrapIcon(icon: Any?): ImageIcon? {
      return when (icon) {
        null -> null
        is Reference<*> -> icon.get() as ImageIcon?
        else -> icon as ImageIcon?
      }
    }
  }

  private val originalResolver = resolver
  private var pathTransformModCount = -1

  @Suppress("LeakingThis")
  private val scaledIconCache = ScaledIconCache(this)

  @Volatile
  private var darkVariant: CachedImageIcon? = null
  private val lock = Any()

  // ImageIcon (if small icon) or SoftReference<ImageIcon> (if large icon)
  @Volatile
  private var realIcon: Any? = null

  constructor(url: URL, useCacheOnLoad: Boolean) : this(originalPath = null,
                                                        resolver = ImageDataByUrlLoader(url, null, useCacheOnLoad),
                                                        isDarkOverridden = null,
                                                        colorPatcher = null) {

    // if url is explicitly specified, it means that path should be not transformed
    pathTransformModCount = pathTransformGlobalModCount.get()
  }

  constructor(originalPath: String?, resolver: ImageDataLoader?) : this(originalPath = originalPath,
                                                                        resolver = resolver,
                                                                        isDarkOverridden = null,
                                                                        colorPatcher = null)

  init {
    // for instance, ShadowPainter updates the context from and outside
    scaleContext.addUpdateListener {
      realIcon = null
    }
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    getRealIcon(ScaleContext.create(if (g is Graphics2D) g else null)).paintIcon(c, g, x, y)
  }

  override fun getIconWidth(): Int = getRealIcon(null).iconWidth

  override fun getIconHeight(): Int = getRealIcon(null).iconHeight

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

      scaledIconCache.getOrScaleIcon(1.0f)?.let { icon ->
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

    // force state update & cache reset
    getRealIcon()
    return scaledIconCache.getOrScaleIcon(scale) ?: this
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
    val ctx = if (useMRI) ScaleContext.create() else ScaleContext.createIdentity()
    ctx.setScale(ScaleType.USR_SCALE.of(1.0))
    var img = loadImage(ctx, isDark)
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
    return CachedImageIcon(originalPath = originalPath,
                           resolver = resolver,
                           isDarkOverridden = isDarkOverridden,
                           localFilterSupplier = localFilterSupplier,
                           colorPatcher = colorPatcher,
                           useStroke = true)
  }

  val isDark: Boolean
    get() = isDarkOverridden ?: pathTransform.get().isDark

  private fun getFilters(): List<ImageFilter> {
    val global = pathTransform.get().filter
    val local = localFilterSupplier?.invoke()
    return when {
      global != null && local != null -> listOf(global, local)
      global != null -> listOf(global)
      else -> listOfNotNull(local)
    }
  }

  val url: URL?
    get() = this.resolver?.url

  internal fun loadImage(scaleContext: ScaleContext, isDark: Boolean): Image? {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val resolver = resolver ?: return null
    val colorPatcher = colorPatcher ?: SVGLoader.colorPatcherProvider
    val image = resolver.loadImage(LoadIconParameters(filters = getFilters(),
                                                      scaleContext = scaleContext,
                                                      isDark = isDark,
                                                      colorPatcher = colorPatcher,
                                                      isStroke = useStroke))
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