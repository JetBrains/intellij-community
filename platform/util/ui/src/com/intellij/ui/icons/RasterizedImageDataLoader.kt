// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.IconManager
import com.intellij.ui.NewUiValue
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.SvgCacheClassifier
import com.intellij.ui.svg.colorPatcherDigestShim
import com.intellij.ui.svg.loadAndCacheIfApplicable
import com.intellij.util.SVGLoader
import kotlinx.serialization.Serializable
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import java.awt.Image
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL
import java.util.function.Supplier
import javax.swing.Icon

// a reflective path is not supported, a result is not cached
/**
 * [expUICacheKey] and [expUIFlags] describe the [expUIPath] file in the same way as [cacheKey] and [flags] describe the [path] file.
 * [expUICacheKey] is 0 if the icon class has no data for the [expUIPath] file.
 */
@ApiStatus.Internal
fun loadRasterizedIcon(
  path: String,
  expUIPath: String?,
  classLoader: ClassLoader,
  cacheKey: Int,
  flags: Int,
  expUICacheKey: Int = 0,
  expUIFlags: Int = 0,
  toolTip: Supplier<String?>?,
): Icon {
  assert(!path.startsWith('/'))
  return CachedImageIcon(loader = RasterizedImageDataLoader(path = path,
                                                            expUIPath = expUIPath,
                                                            classLoaderRef = WeakReference(classLoader),
                                                            cacheKey = cacheKey,
                                                            flags = flags,
                                                            expUICacheKey = expUICacheKey,
                                                            expUIFlags = expUIFlags),
                         toolTip = toolTip)
}

@Serializable
private data class RasterizedImageDataLoaderDescriptor(
  @JvmField val path: String,
  @JvmField val expUIPath: String?,
  @JvmField val pluginId: String,
  @JvmField val moduleId: String?,
  @JvmField val cacheKey: Int,
  @JvmField val flags: Int,
  // the defaults let older data decode
  @JvmField val expUICacheKey: Int = 0,
  @JvmField val expUIFlags: Int = 0,
) : ImageDataLoaderDescriptor {
  override fun createIcon(): ImageDataLoader? {
    ImageDataLoaderStatisticsService.getInstance()?.iconDecoded(expUIPath = expUIPath, expUICacheKey = expUICacheKey)
    val classLoader = IconManager.getInstance().getClassLoader(pluginId, moduleId) ?: return null
    return RasterizedImageDataLoader(path = path,
                                     expUIPath = expUIPath,
                                     classLoaderRef = WeakReference(classLoader),
                                     cacheKey = cacheKey,
                                     flags = flags,
                                     expUICacheKey = expUICacheKey,
                                     expUIFlags = expUIFlags)
  }
}

/**
 * [expUICacheKey] and [expUIFlags] describe the [expUIPath] file in the same way as [cacheKey] and [flags] describe the [path] file.
 * [expUICacheKey] is 0 if the icon class has no data for the [expUIPath] file.
 */
private class RasterizedImageDataLoader(override val path: String,
                                        override val expUIPath: String?,
                                        private val classLoaderRef: WeakReference<ClassLoader>,
                                        private val cacheKey: Int,
                                        override val flags: Int,
                                        private val expUICacheKey: Int,
                                        private val expUIFlags: Int) : ImageDataLoader {
  private val isSvg: Boolean
    get() = cacheKey != 0
  
  override fun getCoords(): Pair<String, ClassLoader>? = classLoaderRef.get()?.let { path to it }

  override fun serializeToByteArray(): ImageDataLoaderDescriptor {
    val classLoader = classLoaderRef.get()!!
    val pluginInfo = IconManager.getInstance().getPluginAndModuleId(classLoader)
    return RasterizedImageDataLoaderDescriptor(
      path = path,
      expUIPath = expUIPath,
      pluginId = pluginInfo.first,
      moduleId = pluginInfo.second,
      flags = flags,
      cacheKey = cacheKey,
      expUICacheKey = expUICacheKey,
      expUIFlags = expUIFlags,
    )
  }

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): ImageWithShape<Image>? {
    ImageDataLoaderStatisticsService.getInstance()?.refineCurrentLoad(cacheKey)
    val classLoader = classLoaderRef.get() ?: return null
    try {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      val image = loadRasterized(path = path,
                                 scaleContext = scaleContext,
                                 parameters = parameters,
                                 classLoader = classLoader,
                                 isSvg = isSvg,
                                 rasterizedCacheKey = cacheKey,
                                 imageFlags = flags)

      if (start != -1L) {
        IconLoadMeasurer.loadFromResources.end(start)
        IconLoadMeasurer.addLoading(isSvg, start)
      }
      return image
    }
    catch (e: IOException) {
      thisLogger().debug(e)
      return null
    }
  }

  override val url: URL?
    get() = classLoaderRef.get()?.getResource(path)

  override fun patch(transform: IconTransform): ImageDataLoader? {
    val classLoader = classLoaderRef.get() ?: return null
    val patched = transform.patchPath(path = path, classLoader = classLoader)
    if (patched == null) {
      if (expUIPath != null && NewUiValue.isEnabled()) {
        if (expUICacheKey == 0) {
          return PatchedRasterizedImageDataLoader(path = expUIPath, classLoaderRef = classLoaderRef, flags = flags)
        }
        return PatchedRasterizedImageDataLoader(path = expUIPath, classLoaderRef = classLoaderRef, flags = expUIFlags, cacheKey = expUICacheKey)
      }
      return null
    }

    if (patched.first.startsWith(FILE_SCHEME_PREFIX)) {
      return ImageDataByFilePathLoader(patched.first)
    }
    else {
      val effectiveClassLoaderRef = patched.second?.let(::WeakReference) ?: classLoaderRef

      if (isReflectivePath(patched.first) && patched.second != null) {
        (getReflectiveIcon(patched.first, patched.second!!) as? CachedImageIcon)?.let {
          val loader = it.loader
          if (loader is RasterizedImageDataLoader) {
            return PatchedRasterizedImageDataLoader(path = loader.path,
                                                    classLoaderRef = effectiveClassLoaderRef,
                                                    flags = loader.flags,
                                                    cacheKey = loader.cacheKey)
          }
          return loader
        }
      }
      return PatchedRasterizedImageDataLoader(path = patched.first, classLoaderRef = effectiveClassLoaderRef, flags = flags)
    }
  }

  override fun isMyClassLoader(classLoader: ClassLoader) = classLoaderRef.get() === classLoader

  override fun toString() = "RasterizedImageDataLoader(classLoader=${classLoaderRef.get()}, path=$path)"
}

/**
 * Loads the [path] that [RasterizedImageDataLoader.patch] selects instead of the original path.
 *
 * If [cacheKey] is not 0, [cacheKey] and [flags] describe the [path] file, and the loader selects the variant by [flags].
 * Otherwise, [flags] belong to the original path, and the loader tries the variants of the [path] file one by one.
 */
private class PatchedRasterizedImageDataLoader(override val path: String,
                                               private val classLoaderRef: WeakReference<ClassLoader>,
                                               override val flags: Int,
                                               private val cacheKey: Int = 0) : ImageDataLoader {
  override fun getCoords(): Pair<String, ClassLoader>? = classLoaderRef.get()?.let { path to it }

  private val isSvg: Boolean
      get() = path.endsWith(".svg")

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): ImageWithShape<Image>? {
    ImageDataLoaderStatisticsService.getInstance()?.refineCurrentLoad(cacheKey)
    val classLoader = classLoaderRef.get() ?: return null
    try {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()

      val image = if (cacheKey == 0) {
        val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
        val dotIndex = path.lastIndexOf('.')
        val name = if (dotIndex < 0) path else path.substring(0, dotIndex)
        // prefer retina images for HiDPI scale, because downscaling retina images provide a better result than up-scaling non-retina images
        val ext = if (isSvg) "svg" else if (dotIndex < 0 || dotIndex == path.length - 1) "" else path.substring(dotIndex + 1)
        loadPatched(name = name,
                    ext = ext,
                    isSvg = isSvg,
                    scale = scale,
                    scaleContext = scaleContext,
                    parameters = parameters,
                    path = path,
                    classLoader = classLoader,
                    isEffectiveDark = parameters.isDark,
                    withShape = flags and ImageDescriptor.IS_MODIFIER_ICON == ImageDescriptor.IS_MODIFIER_ICON)
      }
      else {
        loadRasterized(path = path,
                       scaleContext = scaleContext,
                       parameters = parameters,
                       classLoader = classLoader,
                       isSvg = isSvg,
                       rasterizedCacheKey = cacheKey,
                       imageFlags = flags)
      }

      if (start != -1L) {
        IconLoadMeasurer.loadFromResources.end(start)
        IconLoadMeasurer.addLoading(isSvg, start)
      }
      return image
    }
    catch (e: IOException) {
      thisLogger().debug(e)
      return null
    }
  }

  override val url: URL?
    get() = classLoaderRef.get()?.getResource(normalizePath(path))

  override fun patch(transform: IconTransform): ImageDataLoader? = null

  override fun isMyClassLoader(classLoader: ClassLoader) = classLoaderRef.get() === classLoader

  override fun toString() = "PatchedRasterizedImageDataLoader(classLoader=${classLoaderRef.get()}, path=$path)"
}

private fun loadRasterized(path: String,
                           scaleContext: ScaleContext,
                           parameters: LoadIconParameters,
                           classLoader: ClassLoader,
                           isSvg: Boolean,
                           rasterizedCacheKey: Int,
                           @MagicConstant(flagsFromClass = ImageDescriptor::class) imageFlags: Int): ImageWithShape<Image>? {
  val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
  val dotIndex = path.lastIndexOf('.')
  val name = if (dotIndex < 0) path else path.substring(0, dotIndex)
  val isRetina = scale != 1f
  // prefer retina images for HiDPI scale, because downscaling retina images provide a better result than up-scaling non-retina images
  val ext = if (isSvg) "svg" else if (dotIndex < 0 || dotIndex == path.length - 1) "" else path.substring(dotIndex + 1)

  var isEffectiveDark = parameters.isDark
  val effectivePath: String
  val nonSvgScale: Float
  if (parameters.isStroke && (imageFlags and ImageDescriptor.HAS_STROKE) == ImageDescriptor.HAS_STROKE) {
    effectivePath = "${name}_stroke.$ext"
    nonSvgScale = 1f
  }
  else if (isRetina && parameters.isDark && (imageFlags and ImageDescriptor.HAS_DARK_2x) == ImageDescriptor.HAS_DARK_2x) {
    effectivePath = "$name@2x_dark.$ext"
    nonSvgScale = 2f
  }
  else if (parameters.isDark && imageFlags and ImageDescriptor.HAS_DARK == ImageDescriptor.HAS_DARK) {
    effectivePath = "${name}_dark.$ext"
    nonSvgScale = 1f
  }
  else {
    isEffectiveDark = false
    if (isRetina && imageFlags and ImageDescriptor.HAS_2x == ImageDescriptor.HAS_2x) {
      effectivePath = "$name@2x.$ext"
      nonSvgScale = 2f
    }
    else {
      effectivePath = path
      nonSvgScale = 1f
    }
  }
  val image = if (isSvg) {
    loadSvgFromClassResource(
      classLoader = classLoader,
      path = effectivePath,
      precomputedCacheKey = rasterizedCacheKey,
      scale = scale,
      compoundCacheKey = SvgCacheClassifier(scale = scale, isDark = isEffectiveDark, isStroke = parameters.isStroke),
      colorPatcherProvider = parameters.colorPatcher,
      withShape = imageFlags and ImageDescriptor.IS_MODIFIER_ICON == ImageDescriptor.IS_MODIFIER_ICON
    )
  }
  else {
    loadPngFromClassResource(path = effectivePath, classLoader = classLoader)?.let { result ->
      ImageWithShape(result, null)
    }
  }

  if (image?.image == null) return null

  return ImageWithShape(
    convertImage(
      image = image.image,
      filters = parameters.filters,
      scaleContext = scaleContext,
      isUpScaleNeeded = !isSvg,
      imageScale = nonSvgScale),
    image.shape
  )
}

private class PatchedIconDescriptor(@JvmField val name: String, @JvmField val scale: Float)

private fun loadPatched(name: String,
                        ext: String,
                        isSvg: Boolean,
                        scale: Float,
                        scaleContext: ScaleContext,
                        parameters: LoadIconParameters,
                        path: String,
                        classLoader: ClassLoader,
                        isEffectiveDark: Boolean,
                        withShape: Boolean,
): ImageWithShape<Image>? {
  val stroke = PatchedIconDescriptor("${name}_stroke.$ext", if (isSvg) scale else 1f)
  val retinaDark = PatchedIconDescriptor("$name@2x_dark.$ext", if (isSvg) scale else 2f)
  val dark = PatchedIconDescriptor("${name}_dark.$ext", if (isSvg) scale else 1f)
  val retina = PatchedIconDescriptor("$name@2x.$ext", if (isSvg) scale else 2f)
  val plain = PatchedIconDescriptor(path, if (isSvg) scale else 1f)
  val isRetina = scale != 1f
  val descriptors = when {
    parameters.isStroke -> listOf(stroke, plain)
    isRetina && parameters.isDark -> listOf(retinaDark, dark, retina, plain)
    parameters.isDark -> listOf(dark, plain)
    else -> if (isRetina) listOf(retina, plain) else listOf(plain)
  }

  for (descriptor in descriptors) {
    val image = if (isSvg) {
      loadSvgFromClassResource(classLoader = classLoader,
                               path = descriptor.name,
                               precomputedCacheKey = 0,
                               scale = descriptor.scale,
                               compoundCacheKey = SvgCacheClassifier(scale = descriptor.scale,
                                                                     isDark = isEffectiveDark,
                                                                     isStroke = parameters.isStroke),
                               colorPatcherProvider = parameters.colorPatcher,
                               withShape = withShape)
    }
    else {
      loadPngFromClassResource(path = descriptor.name, classLoader = classLoader)?.let { result ->
        ImageWithShape(result, null)
      }
    }

    if (image?.image != null) {
      return ImageWithShape(
        convertImage(
          image = image.image,
          filters = parameters.filters,
          scaleContext = scaleContext,
          isUpScaleNeeded = !isSvg && (descriptor === plain || descriptor === dark),
          imageScale = descriptor.scale
        ),
        image.shape
      )
    }
  }
  return null
}

private fun loadSvgFromClassResource(classLoader: ClassLoader?,
                                     path: String,
                                     precomputedCacheKey: Int,
                                     scale: Float,
                                     compoundCacheKey: SvgCacheClassifier,
                                     colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?,
                                     withShape: Boolean): ImageWithShape<Image>? {
  return loadAndCacheIfApplicable(path = path,
                                  precomputedCacheKey = precomputedCacheKey,
                                  scale = scale,
                                  compoundCacheKey = compoundCacheKey,
                                  colorPatcherDigest = colorPatcherDigestShim(colorPatcherProvider),
                                  colorPatcher = colorPatcherProvider?.attributeForPath(path),
                                  withShape = withShape) {
    getResourceData(path = path, resourceClass = null, classLoader = classLoader)
  }
}
