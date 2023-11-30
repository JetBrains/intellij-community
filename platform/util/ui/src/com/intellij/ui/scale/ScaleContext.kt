// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale

import com.intellij.ui.JreHiDpiUtil
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

/**
 * Extends [UserScaleContext] with the system scale, and is thus used for raster-based painting.
 * The context is created via a context provider. If the provider is [Component], the context's
 * system scale can be updated via a call to [.update], reflecting the current component's
 * system scale (which may change as the component moves b/w devices).
 *
 * @author tav
 */
class ScaleContext : UserScaleContext {
  companion object {
    /**
     * Creates a context with all scale factors set to 1.
     */
    @JvmStatic
    fun createIdentity(): ScaleContext = ScaleContext(arrayOf(ScaleType.USR_SCALE.of(1f), ScaleType.SYS_SCALE.of(1f)))

    /**
     * Creates a context from the provided `ctx`.
     */
    fun create(context: UserScaleContext): ScaleContext {
      val result = ScaleContext()
      result.update(context)
      return result
    }

    /**
     * Creates a context based on the component's system scale and sticks to it via the [.update] method.
     */
    @JvmStatic
    fun create(component: Component?): ScaleContext {
      if (component == null) {
        return ScaleContext()
      }

      val context = create(component.graphicsConfiguration)
      context.componentRef = WeakReference(component)
      return context
    }

    /**
     * Creates a context based on the GraphicsConfiguration's system scale
     */
    fun create(gc: GraphicsConfiguration?): ScaleContext = ScaleContext(ScaleType.SYS_SCALE.of(JBUIScale.sysScale(gc)))

    /**
     * Creates a context based on the g's system scale
     */
    @JvmStatic
    fun create(g: Graphics2D?): ScaleContext = ScaleContext(ScaleType.SYS_SCALE.of(JBUIScale.sysScale(g)))

    /**
     * Creates a context with the provided scale
     */
    @JvmStatic
    fun create(scale: Scale): ScaleContext = ScaleContext(scale)

    /**
     * Creates a context with the provided scale factors
     */
    @TestOnly
    fun of(scales: Array<Scale>): ScaleContext = ScaleContext(scales = scales)

    /**
     * Creates a default context with the default screen scale and the current user scale
     */
    @JvmStatic
    fun create(): ScaleContext = ScaleContext()
  }

  private var sysScale: Scale = ScaleType.SYS_SCALE.of(JBUIScale.sysScale())
  private var componentRef: WeakReference<Component>? = null

  constructor() {
    pixScale = derivePixScale()
  }

  private constructor(scale: Scale) : this() {
    when (scale.type) {
      ScaleType.USR_SCALE -> {
        usrScale = scale
      }
      ScaleType.OBJ_SCALE -> {
        objScale = scale
      }
      ScaleType.SYS_SCALE -> {
        sysScale = scale
      }
    }
    pixScale = derivePixScale()
  }

  private constructor(scales: Array<Scale>) : this() {
    for (scale in scales) {
      when (scale.type) {
        ScaleType.USR_SCALE -> {
          usrScale = scale
        }
        ScaleType.OBJ_SCALE -> {
          objScale = scale
        }
        ScaleType.SYS_SCALE -> {
          sysScale = scale
        }
      }
    }
    pixScale = derivePixScale()
  }

  override fun derivePixScale(): Double = getScale(DerivedScaleType.DEV_SCALE) * super.derivePixScale()

  /**
   * {@inheritDoc}
   */
  override fun getScale(type: ScaleType): Double = if (type == ScaleType.SYS_SCALE) sysScale.value else super.getScale(type)

  override fun getScaleObject(type: ScaleType): Scale = if (type == ScaleType.SYS_SCALE) sysScale else super.getScaleObject(type)

  /**
   * {@inheritDoc}
   */
  override fun getScale(type: DerivedScaleType): Double = when (type) {
    DerivedScaleType.DEV_SCALE -> if (JreHiDpiUtil.isJreHiDPIEnabled()) sysScale.value else 1.0
    DerivedScaleType.EFF_USR_SCALE -> usrScale.value * objScale.value
    DerivedScaleType.PIX_SCALE -> pixScale
  }

  /**
   * {@inheritDoc}
   * Also updates the system scale (if the context was created from Component) if necessary.
   */
  override fun update(): Boolean {
    var updated = setScale(ScaleType.USR_SCALE.of(JBUIScale.userScale))
    if (componentRef != null) {
      val component = componentRef!!.get()
      if (component != null) {
        updated = setScale(ScaleType.SYS_SCALE.of(JBUIScale.sysScale(component.graphicsConfiguration))) || updated
      }
    }
    return onUpdated(updated)
  }

  /**
   * {@inheritDoc}
   * Also includes the system scale.
   */
  override fun setScale(scale: Scale): Boolean {
    if (isScaleOverridden(scale)) {
      return false
    }

    if (scale.type == ScaleType.SYS_SCALE) {
      val updated = sysScale != scale
      sysScale = scale
      return onUpdated(updated)
    }
    else {
      return super.setScale(scale)
    }
  }

  fun copyWithScale(scale: Scale): ScaleContext {
    val result = ScaleContext(arrayOf(usrScale, sysScale, objScale, scale))
    overriddenScales?.let {
      result.overriddenScales = it.clone()
    }
    return result
  }

  override fun <T : UserScaleContext> updateAll(scaleContext: T): Boolean {
    val updated = super.updateAll(scaleContext)
    if (scaleContext !is ScaleContext) {
      return updated
    }

    componentRef?.clear()
    componentRef = scaleContext.componentRef
    return setScale(scaleContext.sysScale) || updated
  }

  override fun equals(other: Any?): Boolean =
    if (super.equals(other) && other is ScaleContext) other.sysScale.value == sysScale.value else false

  override fun hashCode(): Int = sysScale.value.hashCode() * 31 + super.hashCode()

  override fun dispose() {
    super.dispose()
    componentRef?.clear()
  }

  override fun <T : UserScaleContext> copy(): T {
    val result = ScaleContext(arrayOf(usrScale, sysScale, objScale))
    result.updateAll(this)
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  override fun toString(): String = "$usrScale, $sysScale, $objScale, $pixScale"

  @Deprecated("Use ScaleContextCache")
  open class Cache<T>(dataProvider: Function<in ScaleContext, out T>) : ScaleContextCache<T>(dataProvider::apply)
}

/**
 * A cache for the last usage of a data object matching a scale context.
 *
 * @param dataProvider provides a data object matching the passed scale context
 **/
open class ScaleContextCache<T>(private val dataProvider: (ScaleContext) -> T) {
  private val data = AtomicReference<Pair<Double, T>?>(null)

  /**
   * Returns the data object from the cache if it matches the `ctx`,
   * otherwise provides the new data via the provider and caches it.
   */
  fun getOrProvide(scaleContext: ScaleContext): T? {
    var data = data.get()
    val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE)
    if (data == null || scale.compareTo(data.first) != 0) {
      data = scale to dataProvider(scaleContext)
      this.data.set(data)
    }
    return data.second
  }

  /**
   * Clears the cache.
   */
  fun clear() {
    data.set(null)
  }
}
