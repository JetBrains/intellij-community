// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/setting-values.spec.md

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Represents a setting value in an immutable form that does not depend on the storage.
 *
 * A [Serializer] converts a typed value `T` to and from a tree of these wrappers. A storage backend then reads or
 * writes that tree in its own format, such as JSON, YAML, a binary format or a remote one. The serializer and the
 * platform never learn the format of the backend. Only a [SettingsValue] crosses that boundary.
 *
 * A serialized value is always a **single** [SettingsValue]. A complex object is a tree of [ListValue] and
 * [MapValue] nodes. Every value is immutable, so a value may be shared freely. A public builder of a
 * [ListValue] or a [MapValue] copies the collection it gets, so a later change of that collection does not reach the
 * value.
 */
@Experimental
@Serializable
sealed interface SettingsValue {
  /** The explicit absence of a value. Distinct from "the key was never set". */
  @Experimental
  @Serializable
  data object NullValue : SettingsValue

  @Experimental
  @Serializable
  data class StringValue(val value: String) : SettingsValue

  @Experimental
  @Serializable
  data class IntValue(val value: Int) : SettingsValue

  @Experimental
  @Serializable
  data class LongValue(val value: Long) : SettingsValue

  @Experimental
  @Serializable
  data class DoubleValue(val value: Double) : SettingsValue

  @Experimental
  @Serializable
  data class BooleanValue(val value: Boolean) : SettingsValue

  /**
   * An ordered list of values. It is immutable: each public builder copies the items it gets.
   *
   * @property items the values in their order
   */
  @Experimental
  @Serializable
  @ConsistentCopyVisibility
  data class ListValue private constructor(val items: List<SettingsValue>) : SettingsValue {
    /** Builds a list value of [items], in their order. */
    constructor(vararg items: SettingsValue) : this(items.toList())

    @Experimental
    companion object {
      /**
       * Builds a list value from a copy of [items], so a later change of [items] does not reach the value.
       *
       * @param items the values in their order
       */
      operator fun invoke(items: List<SettingsValue>): ListValue = ListValue(items.toList())

      /**
       * Builds a list value that keeps [items] with no copy. Only for platform code that builds [items] itself and never
       * changes them again.
       *
       * @param items a list that nothing else references or changes
       */
      @Internal
      fun wrapOwned(items: List<SettingsValue>): ListValue = ListValue(items)
    }
  }

  /**
   * A string-keyed map of values. It is immutable: each public builder copies the values it gets.
   *
   * @property values the values by their keys, in the insertion order
   */
  @Experimental
  @Serializable
  @ConsistentCopyVisibility
  data class MapValue private constructor(val values: Map<String, SettingsValue>) : SettingsValue {
    /** `true` when it holds no values. */
    val isEmpty: Boolean get() = values.isEmpty()

    /** Returns the raw value of [key], or `null` when the map holds none. The platform deserializes it. */
    operator fun get(key: SettingsKey<*>): SettingsValue? = this[key.identity]

    /** Returns the raw value of the key identity [keyIdentity], or `null` when the map holds none. The platform deserializes it. */
    operator fun get(keyIdentity: String): SettingsValue? = values[keyIdentity]

    /** Collects the values for a [MapValue]. A built value does not change when the builder is used again. */
    @Experimental
    class Builder {
      private val values: MutableMap<String, SettingsValue> = LinkedHashMap()

      /** Stores an already-serialized [value] under the setting identity [keyId]. This carries a key that is not loaded. */
      operator fun set(keyId: String, value: SettingsValue) {
        values[keyId] = value
      }

      /** Serializes [value] with [key]'s serializer and stores it under the key's [identity][SettingsKey.identity]. */
      operator fun <T> set(key: SettingsKey<T>, value: T) {
        this[key.identity] = key.serializer.serialize(value)
      }

      /** Returns a map value of a copy of the values collected so far. */
      fun build(): MapValue = MapValue(values.toMap())
    }

    @Experimental
    companion object {
      /**
       * Builds a map value from a copy of [values], so a later change of [values] does not reach the value.
       *
       * @param values the values by their keys; the copy keeps their order
       */
      operator fun invoke(values: Map<String, SettingsValue>): MapValue = MapValue(values.toMap())

      /**
       * Builds a map value that keeps [values] with no copy. Only for platform code that builds [values] itself and
       * never changes them again.
       *
       * @param values a map that nothing else references or changes
       */
      @Internal
      fun wrapOwned(values: Map<String, SettingsValue>): MapValue = MapValue(values)
    }
  }

  @Experimental
  companion object {
    /** Holds the empty map value. */
    val EMPTY_MAP: MapValue = MapValue.wrapOwned(emptyMap())
  }
}
