// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/multidimensional-settings.spec.md
// @spec ../../../../../../spec/settings-contexts.spec.md

import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Overlays a base context and pins resolution dimensions. It does not change the identity or the layers of the
 * context. Use it to read or write a multidimensional setting in a slot other than the selected one. It is itself a
 * [SettingsContext] that delegates its structure to the base, so a caller passes it wherever a context is expected.
 * Only [discriminator] and [language] differ. The [LANGUAGE] dimension has the typed [language][Builder.language]
 * shortcut. Other dimensions, such as an inspection profile, use the generic form.
 *
 * ```
 * val access = baseContext.access {
 *   language = kotlinLanguage
 *   this[CURRENT_PROFILE] = "release"
 * }
 * ```
 */
@Experimental
class SettingsAccessContext internal constructor(
  private val baseContext: SettingsContext,
  private val discriminatorOverrides: Map<SettingsKey<*>, Any?>,
) : SettingsContext {
  /**
   * Holds the parent of the base, with the same overrides. The overrides pin the dimensions for the whole
   * resolution, so every ancestor carries them too. The base already holds the built chain, so each link wraps it
   * lazily and copies nothing.
   */
  override val parentContext: SettingsContext? by lazy {
    baseContext.parentContext?.let { SettingsAccessContext(it, discriminatorOverrides) }
  }

  override val kind: SettingsContextKind get() = baseContext.kind
  override val identity: String get() = baseContext.identity

  @Suppress("UNCHECKED_CAST")
  override fun <T> discriminator(key: SettingsKey<T>): T? = discriminatorOverrides[key] as T?

  /** Holds the real context of the base, without the pinned overrides. A write uses it to find the target layer. */
  override val realContext: SettingsContext get() = baseContext.realContext

  override fun equals(other: Any?): Boolean =
    other is SettingsAccessContext && other.baseContext == baseContext && other.discriminatorOverrides == discriminatorOverrides

  override fun hashCode(): Int = 31 * baseContext.hashCode() + discriminatorOverrides.hashCode()

  /**
   * Returns a context over the same base, with these overrides and [build] applied on top. So an access of an access
   * context refines it and does not nest another overlay.
   *
   * @return the refined access context, or the plain base context when [build] clears the last override
   */
  internal fun refine(build: Builder.() -> Unit): SettingsContext {
    val builder = Builder()
    builder.discriminatorOverrides.putAll(discriminatorOverrides)
    builder.apply(build)
    return builder.toContext(baseContext)
  }

  /** Collects the overrides for a [SettingsAccessContext]. */
  @Experimental
  class Builder internal constructor() {
    @PublishedApi
    internal val discriminatorOverrides: MutableMap<SettingsKey<*>, Any?> = LinkedHashMap()

    /** Holds the pinned language, backed by [LANGUAGE]. The default `null` leaves the dimension unspecified. */
    var language: Language?
      get() = discriminatorOverrides[LANGUAGE] as Language?
      set(value) {
        if (value == null) discriminatorOverrides.remove(LANGUAGE) else discriminatorOverrides[LANGUAGE] = value
      }

    /** Pins [discriminator] to [value]. */
    operator fun <T> set(discriminator: SettingsKey<T>, value: T) {
      discriminatorOverrides[discriminator] = value
    }

    /** Returns the access context over [baseContext] with the collected overrides, or [baseContext] itself when none is pinned. */
    internal fun toContext(baseContext: SettingsContext): SettingsContext =
      if (discriminatorOverrides.isEmpty()) baseContext else SettingsAccessContext(baseContext, discriminatorOverrides.toMap())
  }
}

/**
 * Overlays this context with the dimension overrides that [build] collects. Use it to read or write a
 * multidimensional setting in a slot other than the selected one. On an existing access context it does not nest.
 * The result shares the same base and keeps the earlier overrides, and [build] adds to them or replaces them.
 *
 * @return the access context, or this context unchanged when [build] pins nothing
 */
@Experimental
fun SettingsContext.access(build: SettingsAccessContext.Builder.() -> Unit = {}): SettingsContext {
  return if (this is SettingsAccessContext) refine(build)
  else SettingsAccessContext.Builder().apply(build).toContext(this)
}

/**
 * Overlays this context with the value each of [discriminators] resolves to now. A discriminator this context
 * already pins keeps its pinned value. See [accessFor], which pins what one key needs.
 */
@Experimental
fun SettingsContext.access(vararg discriminators: SettingsKey<*>): SettingsContext =
  discriminators.fold(this) { context, discriminator -> context.pin(discriminator) }

/**
 * Overlays this context with the slot that each map key of the [storage chain][SettingsKey.storageChain] of [key]
 * selects now. It also pins the discriminators that those slots need in turn. A discriminator that this context
 * already pins stays as it is.
 *
 * It has two uses:
 * - A write of a slotted [key] needs such a context.
 * - A default factory of a slotted [key] calls it with its own key to learn the slot path of the read. The factory
 *   then reads each pinned value with [SettingsContext.discriminator], and it can look up a default that a plugin
 *   contributes for that slot (ADR 0156). It resolves the discriminators again, so a model change between the read
 *   and the factory can give a newer slot.
 *
 * @return the access context, or this context unchanged for a plain [key]
 */
@Experimental
fun SettingsContext.accessFor(key: SettingsKey<*>): SettingsContext =
  key.storageChain().toList().asReversed().fold(this) { context, mapKey ->
    context.accessFor(mapKey.discriminatorKey).pin(mapKey.discriminatorKey)
  }

/** Overlays this context with [key]'s resolved value. Returns it unchanged if [key] is already pinned. */
private fun <T> SettingsContext.pin(key: SettingsKey<T>): SettingsContext {
  if (discriminator(key) != null) return this
  val pinned = key[this]
  return access { this[key] = pinned }
}
