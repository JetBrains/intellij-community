// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/setting-values.spec.md

import com.intellij.platform.settings.v2.SettingsValue.BooleanValue
import com.intellij.platform.settings.v2.SettingsValue.DoubleValue
import com.intellij.platform.settings.v2.SettingsValue.IntValue
import com.intellij.platform.settings.v2.SettingsValue.ListValue
import com.intellij.platform.settings.v2.SettingsValue.LongValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Converts a value of type [T] to and from a [SettingsValue] tree. It is the contract between a [SettingsKey] and
 * the storage backend. The backend reads or writes that tree in its own format.
 *
 * The platform never inspects [T] through reflection or generics. The serializer alone decides how a value is read
 * and written. The platform owns the key of a setting, and the serializer handles only the *value*.
 *
 * A serializer has no `copy` method. Each read deserializes a fresh instance, so no mutable state is shared.
 */
@Experimental
interface Serializer<T> {
  /** Returns the [SettingsValue] tree of [value]. */
  fun serialize(value: T): SettingsValue

  /**
   * Returns the typed value of the [value] tree.
   *
   * @throws ClassCastException when [value] has the wrong shape for [T]. A read then logs it and falls back to the
   *   default.
   */
  fun deserialize(value: SettingsValue): T
}

/** Serializes an [Int] as an [IntValue]. The scalar shortcut form of [SettingsKey] picks it automatically. */
@Experimental
object IntSerializer : Serializer<Int> {
  override fun serialize(value: Int): SettingsValue = IntValue(value)

  override fun deserialize(value: SettingsValue): Int = (value as IntValue).value
}

/** Serializes a [Long] as a [LongValue]. The scalar shortcut form of [SettingsKey] picks it automatically. */
@Experimental
object LongSerializer : Serializer<Long> {
  override fun serialize(value: Long): SettingsValue = LongValue(value)

  override fun deserialize(value: SettingsValue): Long = (value as LongValue).value
}

/** Serializes a [Double] as a [DoubleValue]. The scalar shortcut form of [SettingsKey] picks it automatically. */
@Experimental
object DoubleSerializer : Serializer<Double> {
  override fun serialize(value: Double): SettingsValue = DoubleValue(value)

  override fun deserialize(value: SettingsValue): Double = (value as DoubleValue).value
}

/** Serializes a [Boolean] as a [BooleanValue]. The scalar shortcut form of [SettingsKey] picks it automatically. */
@Experimental
object BooleanSerializer : Serializer<Boolean> {
  override fun serialize(value: Boolean): SettingsValue = BooleanValue(value)

  override fun deserialize(value: SettingsValue): Boolean = (value as BooleanValue).value
}

/** Serializes a [String] as a [StringValue]. The scalar shortcut form of [SettingsKey] picks it automatically. */
@Experimental
object StringSerializer : Serializer<String> {
  override fun serialize(value: String): SettingsValue = StringValue(value)

  override fun deserialize(value: SettingsValue): String = (value as StringValue).value
}

/**
 * Wraps this serializer so that `null` is a first-class value. A `null` serializes to [SettingsValue.NullValue] and
 * back, and the receiver handles any other value. So a key can declare a nullable type with a `null` default:
 * `SettingsKey<String?>("x", default = null, serializer = StringSerializer.nullable())`. The scalar shortcut cannot
 * infer that, because a `null` default carries no type.
 */
@Experimental
fun <T : Any> Serializer<T>.nullable(): Serializer<T?> = NullableSerializer(this)

private class NullableSerializer<T : Any>(private val delegate: Serializer<T>) : Serializer<T?> {
  override fun serialize(value: T?): SettingsValue = if (value == null) SettingsValue.NullValue else delegate.serialize(value)

  override fun deserialize(value: SettingsValue): T? = if (value is SettingsValue.NullValue) null else delegate.deserialize(value)
}

/**
 * Returns a serializer of a list that serializes each element through this serializer, as a
 * [SettingsValue.ListValue]. It composes with the others, for example `StringSerializer.listOf().nullable()`.
 */
@Experimental
fun <T> Serializer<T>.listOf(): Serializer<List<T>> = ListSerializer(this)

private class ListSerializer<T>(private val element: Serializer<T>) : Serializer<List<T>> {
  override fun serialize(value: List<T>): SettingsValue = ListValue.wrapOwned(value.map(element::serialize))

  override fun deserialize(value: SettingsValue): List<T> = (value as ListValue).items.map(element::deserialize)
}

/**
 * Returns a serializer of a string-keyed map that serializes each value through this serializer, as a [MapValue].
 * The keys of the map become the keys of the [MapValue]. Only [String] keys are supported, because a [MapValue] has
 * only those.
 */
@Experimental
fun <V> Serializer<V>.mapOf(): Serializer<Map<String, V>> = MapEntrySerializer(this)

private class MapEntrySerializer<V>(private val valueSerializer: Serializer<V>) : Serializer<Map<String, V>> {
  override fun serialize(value: Map<String, V>): SettingsValue = MapValue.wrapOwned(value.mapValues { valueSerializer.serialize(it.value) })

  override fun deserialize(value: SettingsValue): Map<String, V> = (value as MapValue).values.mapValues { valueSerializer.deserialize(it.value) }
}

/** Passes a [MapValue] through unchanged. A [SettingsMapKey] uses it, because its value is already a [MapValue]. */
@Experimental
object DumbMapValueSerializer : Serializer<MapValue> {
  override fun serialize(value: MapValue): SettingsValue = value
  override fun deserialize(value: SettingsValue): MapValue = value as MapValue
}

/**
 * Returns the built-in serializer inferred from the runtime class of [default].
 *
 * Backs the scalar shortcut constructor of [SettingsKey]: `SettingsKey<Int>("editor.indent.size", default = 4)`.
 *
 * @throws IllegalStateException when [default] is not one of the scalar types [Int], [Long], [Double], [Boolean] and
 *   [String]
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> inferScalarSerializer(default: T): Serializer<T> = when (default) {
  is Int -> IntSerializer as Serializer<T>
  is Long -> LongSerializer as Serializer<T>
  is Double -> DoubleSerializer as Serializer<T>
  is Boolean -> BooleanSerializer as Serializer<T>
  is String -> StringSerializer as Serializer<T>
  else -> error("Cannot infer a serializer from default '$default'; pass one explicitly (only scalar Int/Long/Double/Boolean/String are inferred).")
}

/**
 * Returns the built-in serializer inferred from the static type [T], with its nullability. [inferScalarSerializer]
 * needs a runtime value instead. A nullable [T] gives a [nullable] serializer, so a key can have no default at all:
 * `SettingsKey<String?>("x")`. Only the scalar types [Int], [Long], [Double], [Boolean] and [String] are supported.
 * Any other type must pass a serializer explicitly.
 *
 * @throws IllegalStateException when [T] is not a supported scalar type
 */
@Experimental
inline fun <reified T> inferSerializer(): Serializer<T> {
  @Suppress("UNCHECKED_CAST")
  val base = when (T::class) {
    Int::class -> IntSerializer
    Long::class -> LongSerializer
    Double::class -> DoubleSerializer
    Boolean::class -> BooleanSerializer
    String::class -> StringSerializer
    else -> error("Cannot infer a serializer for '${T::class.simpleName}'; pass one explicitly (only scalar Int/Long/Double/Boolean/String are inferred).")
  } as Serializer<Any>
  @Suppress("UNCHECKED_CAST")
  return (if (null is T) base.nullable() else base) as Serializer<T>
}
