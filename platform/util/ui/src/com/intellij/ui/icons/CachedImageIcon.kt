// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.SVGLoader
import com.intellij.util.containers.CollectionFactory
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
import java.util.concurrent.ConcurrentMap
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
fun patchIconPath(originalPath: String, classLoader: ClassLoader): Pair<String, ClassLoader>? {
  return pathTransform.get().patchPath(originalPath, classLoader)
}

@JvmField
internal val pathTransform: AtomicReference<IconTransform> = AtomicReference(
  IconTransform(/* dark = */ false, /* patchers = */ arrayOf<IconPathPatcher>(DeprecatedDuplicatesIconPathPatcher()), /* filter = */ null)
)

@JvmField
internal val iconToStrokeIcon: ConcurrentMap<CachedImageIcon, CachedImageIcon> = CollectionFactory.createConcurrentWeakKeyWeakValueMap()

@TestOnly
@ApiStatus.Internal
fun createCachedIcon(file: Path, scaleContext: ScaleContext): CachedImageIcon {
  val path = file.toUri().toString()
  return CachedImageIcon(originalPath = path, resolver = ImageDataByFilePathLoader(path), scaleContext = scaleContext)
}

@ApiStatus.Internal
@ApiStatus.NonExtendable
open class CachedImageIcon internal constructor(
  val originalPath: String?,
  // make not-null as soon as deprecated IconLoader.CachedImageIcon will be removed
  resolver: ImageDataLoader?,
  private val isDarkOverridden: Boolean? = null,
  private val localFilterSupplier: (() -> RGBImageFilter)? = null,
  private val colorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null,
  private val useStroke: Boolean = false,
  private val toolTip: Supplier<String?>? = null,
  private val scaleContext: ScaleContext? = null,
) : CopyableIcon, ScalableIcon, DarkIconProvider, MenuBarIconProvider, IconWithToolTip {
  @Suppress("CanBePrimaryConstructorProperty")
  @Volatile
  var resolver: ImageDataLoader? = resolver

  private val originalResolver = resolver
  private var pathTransformModCount = -1

  private val scaledIconCache = ScaledIconCache()

  @Volatile
  private var darkVariant: CachedImageIcon? = null

  constructor(url: URL, useCacheOnLoad: Boolean, scaleContext: ScaleContext? = null) :
    this(originalPath = null, resolver = ImageDataByUrlLoader(url = url, useCacheOnLoad = useCacheOnLoad), scaleContext = scaleContext) {

    // if url is explicitly specified, it means that path should be not transformed
    pathTransformModCount = pathTransformGlobalModCount.get()
  }

  constructor(originalPath: String?, resolver: ImageDataLoader, toolTip: Supplier<String?>?) :
    this(originalPath = originalPath, resolver = resolver, isDarkOverridden = null, colorPatcher = null, toolTip = toolTip)

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
      scaledIconCache.getCachedIcon(host = this, gc = gc) ?: EMPTY_ICON
    }.paintIcon(c, g, x, y)
  }

  final override fun getIconWidth(): Int = resolveActualIcon().iconWidth

  final override fun getIconHeight(): Int = resolveActualIcon().iconHeight

  final override fun getScale(): Float = 1.0f

  @ApiStatus.Internal
  fun getRealIcon(): Icon = resolveActualIcon()

  @ApiStatus.Internal
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

    this.resolver = originalResolver
    val resolver = this.resolver ?: return
    pathTransformModCount = pathTransformGlobalModCount.get()
    scaledIconCache.clear()
    if (originalPath != null) {
      resolver.patch(originalPath = originalPath, transform = pathTransform.get())?.let {
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

  override fun getDarkIcon(isDark: Boolean): Icon {
    var result = if (isDark) darkVariant else null
    if (result == null) {
      synchronized(scaledIconCache) {
        if (isDark) {
          result = darkVariant
        }

        if (result == null) {
          result = CachedImageIcon(originalPath = originalPath,
                                   resolver = resolver ?: return EMPTY_ICON,
                                   isDarkOverridden = isDark,
                                   localFilterSupplier = localFilterSupplier,
                                   colorPatcher = colorPatcher,
                                   useStroke = useStroke,
                                   toolTip = toolTip,
                                   scaleContext = scaleContext)
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
    scaleContext.setScale(ScaleType.USR_SCALE.of(1f))
    var img = loadImage(scaleContext = scaleContext, isDark = isDark)
    if (useMRI) {
      img = MultiResolutionImageProvider.convertFromJBImage(img)
    }
    return ImageIcon(img ?: return this)
  }

  override fun copy(): Icon {
    val result = CachedImageIcon(originalPath = originalPath,
                                 resolver = resolver ?: return EMPTY_ICON,
                                 isDarkOverridden = isDarkOverridden,
                                 localFilterSupplier = localFilterSupplier,
                                 colorPatcher = colorPatcher,
                                 useStroke = useStroke,
                                 scaleContext = scaleContext?.copy())
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
                           useStroke = useStroke,
                           scaleContext = scaleContext?.copy())
  }

  internal fun createWithPatcher(colorPatcher: SVGLoader.SvgElementColorPatcherProvider): Icon {
    val resolver = resolver ?: return EMPTY_ICON
    return CachedImageIcon(originalPath = originalPath,
                           resolver = resolver,
                           isDarkOverridden = isDarkOverridden,
                           localFilterSupplier = localFilterSupplier,
                           colorPatcher = colorPatcher,
                           useStroke = useStroke,
                           scaleContext = scaleContext?.copy())
  }

  fun createStrokeIcon(): Icon {
    val resolver = resolver ?: return EMPTY_ICON
    return iconToStrokeIcon.computeIfAbsent(this) {
      CachedImageIcon(originalPath = originalPath,
                      resolver = resolver,
                      isDarkOverridden = isDarkOverridden,
                      localFilterSupplier = localFilterSupplier,
                      colorPatcher = colorPatcher,
                      useStroke = true,
                      scaleContext = scaleContext?.copy())
    }
  }

  fun withAnotherIconModifications(useModificationsFrom: CachedImageIcon): Icon {
    if (isDarkOverridden == useModificationsFrom.isDarkOverridden &&
        localFilterSupplier == useModificationsFrom.localFilterSupplier &&
        colorPatcher == useModificationsFrom.colorPatcher &&
        useStroke == useModificationsFrom.useStroke) {
      return this
    }

    return CachedImageIcon(originalPath = originalPath,
                           resolver = resolver,
                           isDarkOverridden = useModificationsFrom.isDarkOverridden,
                           localFilterSupplier = useModificationsFrom.localFilterSupplier,
                           colorPatcher = useModificationsFrom.colorPatcher,
                           useStroke = useModificationsFrom.useStroke,
                           scaleContext = scaleContext?.copy())
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