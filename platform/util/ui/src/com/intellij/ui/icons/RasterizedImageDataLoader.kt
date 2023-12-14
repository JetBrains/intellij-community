// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.IconManager
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
@ApiStatus.Internal
fun loadRasterizedIcon(path: String, classLoader: ClassLoader, cacheKey: Int, flags: Int, toolTip: Supplier<String?>?): Icon {
  assert(!path.startsWith('/'))
  return CachedImageIcon(resolver = RasterizedImageDataLoader(path = path,
                                                              classLoaderRef = WeakReference(classLoader),
                                                              cacheKey = cacheKey,
                                                              flags = flags),
                         toolTip = toolTip)
}

@Serializable
private data class RasterizedImageDataLoaderDescriptor(
  @JvmField val path: String,
  @JvmField val pluginId: String,
  @JvmField val moduleId: String?,
  @JvmField val cacheKey: Int,
  @JvmField val flags: Int,
) : ImageDataLoaderDescriptor {
  override fun createIcon(): ImageDataLoader? {
    val classLoader = IconManager.getInstance().getClassLoader(pluginId, moduleId) ?: return null
    return RasterizedImageDataLoader(path = path, classLoaderRef = WeakReference(classLoader), cacheKey = cacheKey, flags = flags)
  }
}

private class RasterizedImageDataLoader(override val path: String,
                                        private val classLoaderRef: WeakReference<ClassLoader>,
                                        private val cacheKey: Int,
                                        override val flags: Int) : ImageDataLoader {
  override fun getCoords(): Pair<String, ClassLoader>? = classLoaderRef.get()?.let { path to it }

  override fun serializeToByteArray(): ImageDataLoaderDescriptor {
    val classLoader = classLoaderRef.get()!!
    val pluginInfo = IconManager.getInstance().getPluginAndModuleId(classLoader)
    return RasterizedImageDataLoaderDescriptor(
      path = path,
      pluginId = pluginInfo.first,
      moduleId = pluginInfo.second,
      flags = flags,
      cacheKey = cacheKey,
    )
  }

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    val classLoader = classLoaderRef.get() ?: return null
    try {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      val isSvg = cacheKey != 0
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
      return null
    }

    if (patched.first.startsWith(FILE_SCHEME_PREFIX)) {
      return ImageDataByFilePathLoader(patched.first)
    }
    else {
      val effectiveClassLoaderRef = patched.second?.let(::WeakReference) ?: classLoaderRef

      if (isReflectivePath(patched.first) && patched.second != null) {
        (getReflectiveIcon(patched.first, patched.second!!) as? CachedImageIcon)?.let {
          val resolver = it.resolver
          if (resolver is RasterizedImageDataLoader) {
            return PatchedRasterizedImageDataLoader(path = resolver.path, classLoaderRef = effectiveClassLoaderRef, flags = resolver.flags)
          }
          return resolver
        }
      }
      return PatchedRasterizedImageDataLoader(path = patched.first, classLoaderRef = effectiveClassLoaderRef, flags = flags)
    }
  }

  override fun isMyClassLoader(classLoader: ClassLoader) = classLoaderRef.get() === classLoader

  override fun toString() = "RasterizedImageDataLoader(classLoader=${classLoaderRef.get()}, path=$path)"
}

private class PatchedRasterizedImageDataLoader(override val path: String,
                                               private val classLoaderRef: WeakReference<ClassLoader>,
                                               override val flags: Int) : ImageDataLoader {
  override fun getCoords(): Pair<String, ClassLoader>? = classLoaderRef.get()?.let { path to it }

  override fun loadImage(parameters: LoadIconParameters, scaleContext: ScaleContext): Image? {
    val classLoader = classLoaderRef.get() ?: return null
    try {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      val isSvg = path.endsWith(".svg")

      val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
      val dotIndex = path.lastIndexOf('.')
      val name = if (dotIndex < 0) path else path.substring(0, dotIndex)
      // prefer retina images for HiDPI scale, because downscaling retina images provide a better result than up-scaling non-retina images
      val ext = if (isSvg) "svg" else if (dotIndex < 0 || dotIndex == path.length - 1) "" else path.substring(dotIndex + 1)
      val image = loadPatched(name = name,
                              ext = ext,
                              isSvg = isSvg,
                              scale = scale,
                              scaleContext = scaleContext,
                              parameters = parameters,
                              path = path,
                              classLoader = classLoader,
                              isEffectiveDark = parameters.isDark)

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
                           @MagicConstant(flagsFromClass = ImageDescriptor::class) imageFlags: Int): Image? {
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
    )
  }
  else {
    loadPngFromClassResource(path = effectivePath, classLoader = classLoader)
  }

  return convertImage(image = image ?: return null,
                      filters = parameters.filters,
                      scaleContext = scaleContext,
                      isUpScaleNeeded = !isSvg,
                      imageScale = nonSvgScale)
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
                        isEffectiveDark: Boolean): Image? {
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
                               colorPatcherProvider = parameters.colorPatcher)
    }
    else {
      loadPngFromClassResource(path = descriptor.name, classLoader = classLoader)
    }

    if (image != null) {
      return convertImage(image = image,
                          filters = parameters.filters,
                          scaleContext = scaleContext,
                          isUpScaleNeeded = !isSvg && (descriptor === plain || descriptor === dark),
                          imageScale = descriptor.scale)
    }
  }
  return null
}

private fun loadSvgFromClassResource(classLoader: ClassLoader?,
                                     path: String,
                                     precomputedCacheKey: Int,
                                     scale: Float,
                                     compoundCacheKey: SvgCacheClassifier,
                                     colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?): Image? {
  return loadAndCacheIfApplicable(path = path,
                                  precomputedCacheKey = precomputedCacheKey,
                                  scale = scale,
                                  compoundCacheKey = compoundCacheKey,
                                  colorPatcherDigest = colorPatcherDigestShim(colorPatcherProvider),
                                  colorPatcher = colorPatcherProvider?.attributeForPath(path)) {
    getResourceData(path = path, resourceClass = null, classLoader = classLoader)
  }
}
