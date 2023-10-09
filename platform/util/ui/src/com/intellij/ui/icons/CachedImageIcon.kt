// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.SVGLoader
import com.intellij.util.ui.MultiResolutionImageProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.ImageFilter
import java.awt.image.RGBImageFilter
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.ImageIcon

val EMPTY_ICON: ImageIcon by lazy {
  object : ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
    override fun toString(): String = "Empty icon ${super.toString()}"
  }
}

@JvmField
internal var isIconActivated: Boolean = !GraphicsEnvironment.isHeadless()

@JvmField
internal val pathTransformGlobalModCount: AtomicInteger = AtomicInteger()

// opened for https://github.com/search?q=repo%3AJetBrains%2Fjewel%20patchIconPath&type=code
@Internal
fun patchIconPath(originalPath: String, classLoader: ClassLoader): Pair<String, ClassLoader?>? {
  return pathTransform.get().patchPath(originalPath, classLoader)
}

@JvmField
internal val pathTransform: AtomicReference<IconTransform> = AtomicReference(
  IconTransform(dark = false, patchers = arrayOf(DeprecatedDuplicatesIconPathPatcher()), filter = null)
)

@Internal
@ApiStatus.NonExtendable
open class CachedImageIcon private constructor(
  // make not-null as soon as deprecated IconLoader.CachedImageIcon will be removed
  @Volatile
  @JvmField
  internal var resolver: ImageDataLoader?,
  private val isDarkOverridden: Boolean? = null,
  private val localFilterSupplier: (() -> RGBImageFilter)? = null,
  private val colorPatcher: ColorPatcherStrategy = GlobalColorPatcherStrategy,
  private val useStroke: Boolean = false,
  private val toolTip: Supplier<String?>? = null,
  private val scaleContext: ScaleContext? = null,
  private val originalResolver: ImageDataLoader? = resolver,
) : CopyableIcon, ScalableIcon, DarkIconProvider, IconWithToolTip {
  private var pathTransformModCount = -1

  private val scaledIconCache = ScaledIconCache()

  @Volatile
  private var darkVariant: CachedImageIcon? = null

  val originalPath: String?
    get() = originalResolver?.path

  @TestOnly
  internal constructor(file: Path, scaleContext: ScaleContext)
    : this(resolver = ImageDataByFilePathLoader(file.toUri().toString()), scaleContext = scaleContext)

  constructor(url: URL, useCacheOnLoad: Boolean, scaleContext: ScaleContext? = null) :
    this(resolver = ImageDataByUrlLoader(url = url, useCacheOnLoad = useCacheOnLoad), scaleContext = scaleContext) {

    // if url is explicitly specified, it means that path should be not transformed
    pathTransformModCount = pathTransformGlobalModCount.get()
  }

  internal constructor(resolver: ImageDataLoader, toolTip: Supplier<String?>?) :
    this(resolver = resolver, isDarkOverridden = null, toolTip = toolTip)

  @ApiStatus.Experimental
  fun getCoords(): Pair<String, ClassLoader>? = resolver?.getCoords()

  override fun getToolTip(composite: Boolean): String? = toolTip?.get()

  final override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    if (scaleContext != null) {
      resolveActualIcon(scaleContext).paintIcon(c, g, x, y)
      return
    }

    val resolver = resolver
    if (resolver == null || !isIconActivated) {
      return
    }

    val gc = c?.graphicsConfiguration ?: (g as? Graphics2D)?.deviceConfiguration
    synchronized(scaledIconCache) {
      checkPathTransform()
      if (colorPatcher.updateDigest()) {
        scaledIconCache.clear()
      }
      scaledIconCache.getCachedIcon(host = this, gc = gc) ?: EMPTY_ICON
    }.paintIcon(c, g, x, y)
  }

  final override fun getIconWidth(): Int = resolveActualIcon().iconWidth

  final override fun getIconHeight(): Int = resolveActualIcon().iconHeight

  final override fun getScale(): Float = 1.0f

  @Internal
  fun getRealIcon(): Icon = resolveActualIcon()

  @Internal
  fun getRealImage(): Image? = (resolveActualIcon() as? ScaledResultIcon)?.image

  internal fun resolveImage(scaleContext: ScaleContext?): Image? {
    val icon = if (scaleContext == null) resolveActualIcon() else resolveActualIcon(scaleContext)
    return if (icon is ScaledResultIcon) icon.image else null
  }

  private fun resolveActualIcon(scaleContext: ScaleContext): Icon {
    val resolver = resolver
    if (resolver == null || !isIconActivated) {
      return EMPTY_ICON
    }

    synchronized(scaledIconCache) {
      checkPathTransform()
      return scaledIconCache.getOrScaleIcon(host = this, scaleContext = scaleContext) ?: EMPTY_ICON
    }
  }

  private fun resolveActualIcon(): Icon {
    val resolver = resolver
    if (resolver == null || !isIconActivated) {
      return EMPTY_ICON
    }

    synchronized(scaledIconCache) {
      checkPathTransform()

      if (scaleContext == null) {
        return scaledIconCache.getOrScaleIcon(host = this, scaleContext = ScaleContext.create()) ?: EMPTY_ICON
      }
      else {
        scaleContext.update()
        return scaledIconCache.getOrScaleIcon(host = this, scaleContext = scaleContext) ?: EMPTY_ICON
      }
    }
  }

  private fun checkPathTransform() {
    if (pathTransformModCount == pathTransformGlobalModCount.get()) {
      return
    }

    resolver = originalResolver
    pathTransformModCount = pathTransformGlobalModCount.get()
    scaledIconCache.clear()
    if (originalPath != null) {
      resolver?.patch(transform = pathTransform.get())?.let {
        this.resolver = it
      }
    }
  }

  override fun toString(): String = resolver?.toString() ?: (originalPath ?: "unknown path")

  override fun scale(scale: Float): Icon {
    if (scale == 1.0f) {
      return this
    }
    else if (scaleContext == null) {
      return scaledIconCache.getOrScaleIcon(host = this, scaleContext = ScaleContext.create(ScaleType.OBJ_SCALE.of(scale))) ?: this
    }
    else {
      return scaledIconCache.getOrScaleIcon(host = this, scaleContext = scaleContext.copyWithScale(ScaleType.OBJ_SCALE.of(scale))) ?: this
    }
  }

  fun scale(scaleContext: ScaleContext): Icon {
    return scaledIconCache.getOrScaleIcon(host = this, scaleContext = scaleContext) ?: this
  }

  fun scale(scale: Float, ancestor: Component?): Icon {
    if (scale == 1.0f && ancestor == null) {
      return this
    }

    val scaleContext = if (ancestor == null && scaleContext != null) ScaleContext.create(scaleContext) else ScaleContext.create(ancestor)
    scaleContext.setScale(ScaleType.OBJ_SCALE.of(scale))
    return scaledIconCache.getOrScaleIcon(host = this, scaleContext = scaleContext) ?: this
  }

  override fun copy(): Icon = copy(isDarkOverridden = isDarkOverridden)

  private fun copy(
    isDarkOverridden: Boolean? = this.isDarkOverridden,
    localFilterSupplier: (() -> RGBImageFilter)? = this.localFilterSupplier,
    colorPatcher: ColorPatcherStrategy = this.colorPatcher,
    useStroke: Boolean = this.useStroke,
  ): CachedImageIcon {
    val result = CachedImageIcon(resolver = resolver,
                                 originalResolver = originalResolver,
                                 isDarkOverridden = isDarkOverridden,
                                 localFilterSupplier = localFilterSupplier,
                                 colorPatcher = colorPatcher,
                                 useStroke = useStroke,
                                 toolTip = toolTip,
                                 scaleContext = scaleContext?.copy())
    result.pathTransformModCount = pathTransformModCount
    return result
  }

  override fun getDarkIcon(isDark: Boolean): CachedImageIcon {
    if (isDarkOverridden != null && isDarkOverridden == isDark) {
      return this
    }

    var result = if (isDark) darkVariant else null
    if (result == null) {
      synchronized(scaledIconCache) {
        if (isDark) {
          result = darkVariant
        }

        if (result == null) {
          result = copy(isDarkOverridden = isDark)
          if (isDark) {
            darkVariant = result
          }
        }
      }
    }
    return result!!
  }

  fun getMenuBarIcon(isDark: Boolean): Icon {
    val useMultiResolution = SystemInfoRt.isMac
    val scaleContext = if (useMultiResolution) ScaleContext.create() else ScaleContext.createIdentity()
    scaleContext.setScale(ScaleType.USR_SCALE.of(1f))

    checkPathTransform()

    var image = loadImage(scaleContext = scaleContext, isDark = isDark)
    if (useMultiResolution) {
      image = MultiResolutionImageProvider.convertFromJBImage(image)
    }
    return ImageIcon(image ?: return this)
  }

  internal fun createWithFilter(filterSupplier: () -> RGBImageFilter): Icon = copy(localFilterSupplier = filterSupplier)

  fun createWithPatcher(colorPatcher: SVGLoader.SvgElementColorPatcherProvider,
                        useStroke: Boolean = this.useStroke,
                        isDark: Boolean? = null): Icon {
    return copy(colorPatcher = CustomColorPatcherStrategy(colorPatcher), useStroke = useStroke, isDarkOverridden = isDark)
  }

  internal fun createStrokeIcon(): CachedImageIcon = copy(useStroke = true)

  fun withAnotherIconModifications(useModificationsFrom: CachedImageIcon): Icon {
    if (isDarkOverridden == useModificationsFrom.isDarkOverridden &&
        localFilterSupplier == useModificationsFrom.localFilterSupplier &&
        colorPatcher === useModificationsFrom.colorPatcher &&
        useStroke == useModificationsFrom.useStroke) {
      return this
    }

    return CachedImageIcon(resolver = resolver,
                           originalResolver = originalResolver,
                           isDarkOverridden = useModificationsFrom.isDarkOverridden,
                           localFilterSupplier = useModificationsFrom.localFilterSupplier,
                           colorPatcher = useModificationsFrom.colorPatcher,
                           useStroke = useModificationsFrom.useStroke,
                           scaleContext = scaleContext?.copy())
  }

  internal val isDark: Boolean
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
                                                                   colorPatcher = colorPatcher.colorPatcher,
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

    synchronized(scaledIconCache) {
      val resolver = resolver ?: return true
      val originalResolver = originalResolver
      if (!resolver.isMyClassLoader(loader) && !(originalResolver != null && originalResolver.isMyClassLoader(loader))) {
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
      return (resolver ?: return 0).flags
    }
}

@TestOnly
@Internal
fun createCachedIcon(file: Path, scaleContext: ScaleContext): CachedImageIcon = CachedImageIcon(file, scaleContext = scaleContext)

private sealed interface ColorPatcherStrategy {
  /**
   * Returns true if cache invalidation is required.
   */
  fun updateDigest(): Boolean

  val colorPatcher: SVGLoader.SvgElementColorPatcherProvider?
}

private data object GlobalColorPatcherStrategy : ColorPatcherStrategy {
  override fun updateDigest() = false

  override val colorPatcher: SVGLoader.SvgElementColorPatcherProvider?
    get() = SVGLoader.colorPatcherProvider
}

private class CustomColorPatcherStrategy(override val colorPatcher: SVGLoader.SvgElementColorPatcherProvider) : ColorPatcherStrategy {
  private val lastDigest = AtomicReference(colorPatcher.digest())

  override fun updateDigest(): Boolean {
    val digest = colorPatcher.digest()
    return !lastDigest.getAndSet(digest).contentEquals(digest)
  }
}