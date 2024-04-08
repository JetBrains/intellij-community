// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html.image

import com.github.weisj.jsvg.nodes.SVG
import com.intellij.ui.icons.HiDPIImage
import com.intellij.util.DataUrl
import com.intellij.util.MemorySizeAware
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import java.awt.image.BufferedImage
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Versatile image wrapper which supports:
 *  - raster and vector (SVG) images
 *  - re-rasterization of vector images based on viewport size
 *  - Unloading of the content to avoid memory runaway
 *
 *  @see AdaptiveImageView
 */
class UnloadableAdaptiveImage(
  src: AdaptiveImageSource,
  value: LoadedAdaptiveImage
) : Unloadable<AdaptiveImageSource, LoadedAdaptiveImage>(src, value)

class UnloadableRasterizedImage(
  src: SVGRasterizationConfig,
  value: RasterizedVectorImage
) : Unloadable<SVGRasterizationConfig, RasterizedVectorImage>(src, value)

data class SVGRasterizationConfig(val svgImage: LoadedSVGImage, val logicalWidth: Float, val logicalHeight: Float, val scale: Float)

class RasterizedVectorImage(val image: HiDPIImage) : MemorySizeAware {
  override fun getMemorySize(): Long {
    return image.width.toLong() * image.height * 4 /*  assume rgba */
  }
}

sealed class LoadedAdaptiveImage(val dimensions: ImageDimensions, private val memorySize: Long) : MemorySizeAware {
  override fun getMemorySize(): Long {
    return memorySize
  }
}

class LoadedSVGImage(val src: String, val svgNode: SVG, dimensions: ImageDimensions, memorySize: Long) : LoadedAdaptiveImage(dimensions, memorySize)

class LoadedRasterImage(val image: BufferedImage, dimensions: ImageDimensions, memorySize: Long) : LoadedAdaptiveImage(dimensions, memorySize)

data class ImageDimensions(val width: ImageDimension, val height: ImageDimension, val fallBack: FloatDimensions) {
  constructor(width: Float, widthUnit: ImageDimension.Unit, height: Float, heightUnit: ImageDimension.Unit, fallbackWidth: Float, fallbackHeight: Float)
    : this(ImageDimension(widthUnit, width), ImageDimension(heightUnit, height), FloatDimensions(fallbackWidth, fallbackHeight))
}

data class ImageDimension(val unit: Unit, val value: Float) {
  enum class Unit { PX, EM, EX, PERCENTAGE }
}

data class FloatDimensions(val width: Float, val height: Float)

interface AdaptiveImageSource
data class DataUrlAdaptiveImageSource(val dataUrl: DataUrl) : AdaptiveImageSource {
  override fun toString() = "DataUrlAdaptiveImageSource(${dataUrl.toString(includeClassName = false, stripContent = true)})"
}

sealed interface AdaptiveImageOrigin {
  data class Url(val url: String) : AdaptiveImageOrigin

  data class DataUrl(val dataUrl: com.intellij.util.DataUrl) : AdaptiveImageOrigin
}

interface UnloadableManager<S, T : MemorySizeAware> {
  fun onUnload(u: Unloadable<S, T>)

  fun notifyUsed(u: Unloadable<S, T>)
}

sealed interface AdaptiveImageRendererEvent {

  data class Loaded(val dimensions: ImageDimensions, val vector: Boolean) : AdaptiveImageRendererEvent

  data class Rasterized(val dimensions: ImageDimensions, val vector: Boolean) : AdaptiveImageRendererEvent

  class Error : AdaptiveImageRendererEvent

  class Unloaded : AdaptiveImageRendererEvent
}

interface AdaptiveImageRenderer {
  fun setOrigin(origin: AdaptiveImageOrigin?)
  fun setRenderConfig(width: Float, height: Float, scale: Float)
  fun getRenderedImage(): BufferedImage?
  fun invalidate(reload: Boolean)

  @TestOnly
  fun hasError(): Boolean

  @TestOnly
  fun resetError()
}

interface AdaptiveImagesManager {
  fun createRenderer(rendererScope: CoroutineScope, eventListener: (AdaptiveImageRendererEvent) -> Unit): AdaptiveImageRenderer

  /**
   * Creates new renderer using GlobalScope as parent scope and Dispatchers.EDT as dispatcher
   */
  fun createRenderer(eventListener: (AdaptiveImageRendererEvent) -> Unit): AdaptiveImageRenderer
}


abstract class Unloadable<S, T : MemorySizeAware>(val src: S, initialValue: T) {
  private var myValue: T? = initialValue
  private var myLastUsedNs: Long = 0
  private var myManager: UnloadableManager<S, T>? = null

  val lastUsedNs: Long
    get() = myLastUsedNs

  val value: T?
    get() {
      myLastUsedNs = System.nanoTime()
      myManager?.notifyUsed(this)
      return myValue
    }

  val loaded: Boolean
    get() = myValue != null

  internal fun setManager(mgr: UnloadableManager<S, T>) {
    if (myManager != null && myManager != mgr) {
      throw IllegalStateException("Already registered with another manager")
    }

    myManager = mgr
  }

  fun unload() {
    myValue = null
    myManager?.onUnload(this)
  }
}

class UnloadableCache<S, V : MemorySizeAware, T : Unloadable<S, V>> : UnloadableManager<S, V>, MemorySizeAware {
  class MyReference<S, V : MemorySizeAware, T : Unloadable<S, V>>(
    val key: S,
    ref: T,
    refQueue: ReferenceQueue<T>
  ) : WeakReference<T>(ref, refQueue) {
    var mySize = ref.value?.memorySize ?: 0
  }

  private val myMap = LinkedHashMap<S, MyReference<S, V, T>>(16, 0.75f, true)
  private val myRefQueue = ReferenceQueue<T>()
  private var myMemorySize: Long = 0

  val values: List<T>
    get() = myMap.values.map { it.get() }.filterNotNull().toList()

  private fun processRefQueue() {
    while (true) {
      val ref = (myRefQueue.poll() as MyReference<S, V, T>?) ?: break
      myMemorySize -= ref.mySize
      myMap.remove(ref.key)
    }
  }

  fun register(u: T) {
    processRefQueue()
    val oldRef = myMap.remove(u.src)
    if (oldRef != null) {
      myMemorySize -= oldRef.mySize
    }

    val newRef = MyReference(u.src, u, myRefQueue)

    u.setManager(this)
    myMemorySize += newRef.mySize
    myMap[u.src] = newRef
  }

  fun get(src: S): T? = myMap[src]?.get()

  fun getLRUValue(): T? {
    processRefQueue()
    val iterator = myMap.values.iterator()
    while (iterator.hasNext()) {
      val ref = iterator.next()
      val value = ref.get()
      if (value != null) {
        return value
      }
      iterator.remove()
    }

    return null
  }

  override fun onUnload(u: Unloadable<S, V>) {
    processRefQueue()
    val ref = myMap.remove(u.src) ?: return
    myMemorySize -= ref.mySize
    ref.mySize = 0
  }

  override fun notifyUsed(u: Unloadable<S, V>) {
    val ref = myMap[u.src] ?: return
    myMap[u.src] = ref
  }

  override fun getMemorySize() = myMemorySize

  @TestOnly
  fun conditionalUnload(predicate: (T) -> Boolean) {
    val tmpList = myMap.values
      .mapNotNull { it.get() }
      .filter(predicate)
      .toList()

    tmpList.forEach { it.unload() }
  }
}
