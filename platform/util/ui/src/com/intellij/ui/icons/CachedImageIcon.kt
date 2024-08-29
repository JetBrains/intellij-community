// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.svg.colorPatcherDigestShim
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.SVGLoader
import com.intellij.util.ui.MultiResolutionImageProvider
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.ImageFilter
import java.net.URL
import java.nio.file.Path
import java.util.Objects
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
  internal var loader: ImageDataLoader?,
  private val localFilterSupplier: RgbImageFilterSupplier? = null,
  private val colorPatcher: ColorPatcherStrategy = GlobalColorPatcherStrategy,
  private val toolTip: Supplier<String?>? = null,
  private val scaleContext: ScaleContext? = null,
  @Internal val originalLoader: ImageDataLoader? = loader,
  // Do not use it directly for rendering - use `getEffectiveAttributes`
  // isDark is not defined in most cases, and we use a global state at the call moment.
  private val attributes: IconAttributes = IconAttributes(),
  private val iconCache: ScaledIconCache = ScaledIconCache(),
) : CopyableIcon, ScalableIcon, DarkIconProvider, IconPathProvider, IconWithToolTip {
  private var pathTransformModCount = -1

  override val originalPath: String?
    get() = originalLoader?.path

  override val expUIPath: String?
    get() = originalLoader?.expUIPath

  @TestOnly
  internal constructor(file: Path, scaleContext: ScaleContext)
    : this(loader = ImageDataByFilePathLoader(file.toUri().toString()), scaleContext = scaleContext)

  internal constructor(file: Path) : this(loader = ImageDataByFilePathLoader(file.toUri().toString()))

  constructor(url: URL, scaleContext: ScaleContext? = null) :
    this(loader = ImageDataByUrlLoader(url = url), scaleContext = scaleContext) {

    // if url is explicitly specified, it means that path should be not transformed
    pathTransformModCount = pathTransformGlobalModCount.get()
  }

  @Internal
  constructor(loader: ImageDataLoader) : this(loader = loader, originalLoader = loader)

  internal constructor(loader: ImageDataLoader, toolTip: Supplier<String?>?) :
    this(loader = loader, originalLoader = loader, toolTip = toolTip)

  internal constructor(loader: ImageDataLoader, toolTip: Supplier<String?>?, originalLoader: ImageDataLoader?) :
    this(loader = loader, originalLoader = originalLoader, toolTip = toolTip, scaleContext = null)

  private fun getEffectiveAttributes(): IconAttributes {
    return if (attributes.isDarkSet) attributes else attributes.copy(isDark = pathTransform.get().isDark, isDarkSet = true)
  }

  @ApiStatus.Experimental
  fun getCoords(): Pair<String, ClassLoader>? = loader?.getCoords()

  override fun getToolTip(composite: Boolean): String? = toolTip?.get()

  final override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val gc = c?.graphicsConfiguration ?: (g as? Graphics2D)?.deviceConfiguration

    if (scaleContext != null) {
      // We must use the icon's object scale, but not its sys scale,
      // as it might be different from the one of the provided component / graphics.
      resolveActualIcon(scaleContext.copyWithScale(ScaleType.SYS_SCALE.of(JBUIScale.sysScale(gc)))).paintIcon(c, g, x, y)
      return
    }

    synchronized(iconCache) {
      checkPathTransform()
      iconCache.getCachedIcon(host = this, gc = gc, attributes = getEffectiveAttributes())
    }.paintIcon(c, g, x, y)
  }

  final override fun getIconWidth(): Int = resolveActualIcon().iconWidth

  fun getRawIconWidth(): Int {
    synchronized(iconCache) {
      checkPathTransform()
      return iconCache.getCachedIcon(host = this, gc = null, attributes = getEffectiveAttributes()).iconWidth
    }
  }

  final override fun getIconHeight(): Int = resolveActualIcon().iconHeight

  final override fun getScale(): Float = 1.0f

  @Internal
  fun getRealIcon(): Icon = resolveActualIcon()

  @Internal
  fun getRealImage(): Image? {
    val image = (resolveActualIcon() as? ScaledResultIcon)?.image
    return (image as? JBHiDPIScaledImage)?.delegate ?: image
  }

  internal fun resolveImage(scaleContext: ScaleContext?): Image? {
    val icon = if (scaleContext == null) resolveActualIcon() else resolveActualIcon(scaleContext)
    return if (icon is ScaledResultIcon) icon.image else null
  }

  private fun resolveActualIcon(scaleContext: ScaleContext): Icon {
    synchronized(iconCache) {
      checkPathTransform()
      return iconCache.getOrScaleIcon(host = this, scaleContext = scaleContext, attributes = getEffectiveAttributes())
    }
  }

  private fun resolveActualIcon(): Icon {
    synchronized(iconCache) {
      checkPathTransform()

      if (scaleContext == null) {
        return iconCache.getCachedIcon(host = this, gc = null, attributes = getEffectiveAttributes())
      }
      else {
        scaleContext.update()
        return iconCache.getOrScaleIcon(host = this, scaleContext = scaleContext, attributes = getEffectiveAttributes())
      }
    }
  }

  private fun checkPathTransform() {
    if (colorPatcher.updateDigest()) {
      iconCache.clear()
    }

    if (pathTransformModCount == pathTransformGlobalModCount.get()) {
      return
    }

    loader = originalLoader
    pathTransformModCount = pathTransformGlobalModCount.get()
    iconCache.clear()
    if (originalPath != null) {
      loader?.patch(transform = pathTransform.get())?.let {
        this.loader = it
      }
    }
  }

  override fun toString(): String = loader?.toString() ?: (originalPath ?: "unknown path")

  override fun scale(scale: Float): CachedImageIcon {
    return when {
      scale == 1.0f -> this
      scaleContext == null -> copy(scaleContext = ScaleContext.create(ScaleType.OBJ_SCALE.of(scale)))
      else -> copy(scaleContext = scaleContext.copyWithScale(ScaleType.OBJ_SCALE.of(scale)))
    }
  }

  @Internal
  fun getObjScale(): Double? = scaleContext?.getScale(ScaleType.OBJ_SCALE)

  fun scale(scaleContext: ScaleContext): Icon {
    return iconCache.getOrScaleIcon(host = this, scaleContext = scaleContext, attributes = getEffectiveAttributes())
  }

  fun scale(scale: Float, ancestor: Component? = null, isDark: Boolean? = null): CachedImageIcon {
    if (scale == 1.0f && ancestor == null) {
      return this
    }

    val scaleContext = if (ancestor == null) {
      @Suppress("IfThenToElvis")
      if (scaleContext == null) {
        ScaleContext.create(ScaleType.OBJ_SCALE.of(scale))
      }
      else {
        scaleContext.copyWithScale(ScaleType.OBJ_SCALE.of(scale))
      }
    }
    else {
      ScaleContext.create(ancestor).also {
        it.setScale(ScaleType.OBJ_SCALE.of(scale))
      }
    }

    var newAttributes = attributes
    if (isDark != null) {
      newAttributes = newAttributes.copy(isDark = isDark, isDarkSet = true)
    }
    return copy(scaleContext = scaleContext, attributes = newAttributes)
  }

  override fun copy(): Icon = copy(attributes = attributes)

  private fun copy(
    attributes: IconAttributes = this.attributes,
    localFilterSupplier: RgbImageFilterSupplier? = this.localFilterSupplier,
    colorPatcher: ColorPatcherStrategy = this.colorPatcher,
    scaleContext: ScaleContext? = this.scaleContext?.copy(),
  ): CachedImageIcon {
    val reuseIconCache = localFilterSupplier === this.localFilterSupplier && colorPatcher === this.colorPatcher
    val result = CachedImageIcon(
      loader = loader,
      originalLoader = originalLoader,
      attributes = attributes,
      localFilterSupplier = localFilterSupplier,
      colorPatcher = colorPatcher,
      toolTip = toolTip,
      scaleContext = scaleContext,
      iconCache = if (reuseIconCache) iconCache else ScaledIconCache(),
    )
    result.pathTransformModCount = pathTransformModCount
    return result
  }

  fun withAnotherIconModifications(useModificationsFrom: CachedImageIcon): Icon {
    if (attributes == useModificationsFrom.attributes &&
        localFilterSupplier == useModificationsFrom.localFilterSupplier &&
        colorPatcher === useModificationsFrom.colorPatcher) {
      return this
    }

    return copy(attributes = useModificationsFrom.attributes,
                localFilterSupplier = useModificationsFrom.localFilterSupplier,
                colorPatcher = useModificationsFrom.colorPatcher)
  }

  override fun getDarkIcon(isDark: Boolean): CachedImageIcon {
    if (attributes.isDarkSet && attributes.isDark == isDark) {
      return this
    }
    else {
      return copy(attributes = attributes.copy(isDark = isDark, isDarkSet = true))
    }
  }

  fun getMenuBarIcon(isDark: Boolean): Icon {
    val useMultiResolution = SystemInfoRt.isMac
    val scaleContext = if (useMultiResolution) ScaleContext.create() else ScaleContext.createIdentity()
    scaleContext.setScale(ScaleType.USR_SCALE.of(1f))

    checkPathTransform()

    val icon = iconCache.getOrScaleIcon(scaleContext = scaleContext,
                                        attributes = attributes.copy(isDark = isDark, isDarkSet = true),
                                        host = this)
    if (useMultiResolution) {
      return ImageIcon(MultiResolutionImageProvider.convertFromJBImage((icon as ScaledResultIcon).image))
    }
    else {
      return icon
    }
  }

  internal fun createWithFilter(filterSupplier: RgbImageFilterSupplier): Icon = copy(localFilterSupplier = filterSupplier)

  fun createWithPatcher(colorPatcher: SVGLoader.SvgElementColorPatcherProvider,
                        useStroke: Boolean = attributes.useStroke,
                        isDark: Boolean? = null): Icon {
    var newAttributes = attributes.copy(useStroke = useStroke)
    if (isDark != null) {
      newAttributes = newAttributes.copy(isDark = isDark, isDarkSet = true)
    }
    return copy(colorPatcher = CustomColorPatcherStrategy(colorPatcher), attributes = newAttributes)
  }

  internal fun createStrokeIcon(): CachedImageIcon = copy(attributes.copy(useStroke = true))

  private fun getFilters(): List<ImageFilter> {
    val global = pathTransform.get().filter
    val local = localFilterSupplier?.getFilter()
    return if (global != null && local != null) listOf(global, local) else listOfNotNull(global ?: local)
  }

  val url: URL?
    get() = synchronized(iconCache) {
      checkPathTransform()
      this.loader?.url
    }

  internal fun loadImage(scaleContext: ScaleContext, attributes: IconAttributes): Image? {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val loader = loader ?: return null

    val image = loader.loadImage(parameters = LoadIconParameters(filters = getFilters(),
                                                                 isDark = attributes.isDark,
                                                                 colorPatcher = colorPatcher.colorPatcher,
                                                                 isStroke = attributes.useStroke),
                                 scaleContext = scaleContext)
    if (start != -1L) {
      IconLoadMeasurer.findIconLoad.end(start)
    }
    return image
  }

  internal fun detachClassLoader(classLoader: ClassLoader): Boolean {
    if (loader == null) {
      return true
    }

    synchronized(iconCache) {
      val loader = loader ?: return true
      val originalLoader = originalLoader
      if (!loader.isMyClassLoader(classLoader) && !(originalLoader != null && originalLoader.isMyClassLoader(classLoader))) {
        return false
      }

      this.loader = null
      iconCache.clear()
      return true
    }
  }

  fun encodeToByteArray(): ByteArray {
    var descriptor = originalLoader?.serializeToByteArray()
    if (descriptor == null) {
      descriptor = UrlDataLoaderDescriptor(url!!.toExternalForm())
    }
    return ProtoBuf.encodeToByteArray(descriptor)
  }

  val imageFlags: Int
    get() = loader?.flags ?: 0

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other === null -> false
    else -> (other as? CachedImageIcon)?.let {
      localFilterSupplier == it.localFilterSupplier &&
      colorPatcher == it.colorPatcher &&
      toolTip == it.toolTip &&
      scaleContext == it.scaleContext &&
      originalLoader == it.originalLoader &&
      attributes.flags == it.attributes.flags
    } == true
  }

  override fun hashCode(): Int = Objects.hash(
    localFilterSupplier, colorPatcher, toolTip, scaleContext, originalLoader, attributes.flags)

}

@TestOnly
@Internal
fun createCachedIcon(file: Path, scaleContext: ScaleContext): CachedImageIcon = CachedImageIcon(file, scaleContext = scaleContext)

@Serializable
private data class UrlDataLoaderDescriptor(
  @JvmField val url: String,
) : ImageDataLoaderDescriptor {
  override fun createIcon(): ImageDataLoader {
    return ImageDataByFilePathLoader(url)
  }
}

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
  @Volatile
  private var lastDigest = colorPatcherDigestShim(colorPatcher)

  override fun updateDigest(): Boolean {
    val digest = colorPatcher.digest()
    if (lastDigest.contentEquals(digest)) {
      return false
    }
    lastDigest = digest
    return true
  }
}

@Internal
fun decodeCachedImageIconFromByteArray(byteArray: ByteArray): Icon? {
  val descriptor = ProtoBuf.decodeFromByteArray<ImageDataLoaderDescriptor>(byteArray)
  return CachedImageIcon(descriptor.createIcon() ?: return null)
}