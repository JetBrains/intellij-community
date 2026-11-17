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
 *
 * A stored value can be incorrect, because a user edits the config by hand. A deserializer does its best to restore the
 * value. When it cannot, it throws [IllegalArgumentException] with a message that says what is wrong, and the read
 * falls back to the default of the key. It throws no other exception for an incorrect value.
 */
@Experimental
interface Serializer<T> {
  /** Returns the [SettingsValue] tree of [value]. */
  fun serialize(value: T): SettingsValue

  /**
   * Returns the typed value of the [value] tree.
   *
   * @throws IllegalArgumentException when [value] is incorrect for [T] in any way, for example a value of an unexpected
   *   type or shape, or an unknown name. A read then logs a warning and falls back to the default.
   */
  @Throws(IllegalArgumentException::class)
  fun deserialize(value: SettingsValue): T
}

/**
 * Returns this stored value as a [V], for a deserializer that expects that type of value.
 *
 * @param target names what the deserializer restores, such as an enum class, for the message of the failure, or `null`
 * @throws IllegalArgumentException when this value is not a [V]
 */
internal inline fun <reified V : SettingsValue> SettingsValue.requireValue(target: String? = null): V =
  this as? V ?: throw IllegalArgumentException(
    "Expected a stored ${V::class.java.simpleName}${target?.let { " of $it" }.orEmpty()}, but found $this")

/** Serializes an [Int] as an [IntValue]. The eager-default constructor of [SettingsKey] infers it. */
@Experimental
object IntSerializer : Serializer<Int> {
  override fun serialize(value: Int): SettingsValue = IntValue(value)

  override fun deserialize(value: SettingsValue): Int = value.requireValue<IntValue>().value
}

/** Serializes a [Long] as a [LongValue]. The eager-default constructor of [SettingsKey] infers it. */
@Experimental
object LongSerializer : Serializer<Long> {
  override fun serialize(value: Long): SettingsValue = LongValue(value)

  override fun deserialize(value: SettingsValue): Long = value.requireValue<LongValue>().value
}

/** Serializes a [Double] as a [DoubleValue]. The eager-default constructor of [SettingsKey] infers it. */
@Experimental
object DoubleSerializer : Serializer<Double> {
  override fun serialize(value: Double): SettingsValue = DoubleValue(value)

  override fun deserialize(value: SettingsValue): Double = value.requireValue<DoubleValue>().value
}

/** Serializes a [Boolean] as a [BooleanValue]. The eager-default constructor of [SettingsKey] infers it. */
@Experimental
object BooleanSerializer : Serializer<Boolean> {
  override fun serialize(value: Boolean): SettingsValue = BooleanValue(value)

  override fun deserialize(value: SettingsValue): Boolean = value.requireValue<BooleanValue>().value
}

/** Serializes a [String] as a [StringValue]. The eager-default constructor of [SettingsKey] infers it. */
@Experimental
object StringSerializer : Serializer<String> {
  override fun serialize(value: String): SettingsValue = StringValue(value)

  override fun deserialize(value: SettingsValue): String = value.requireValue<StringValue>().value
}

/**
 * Wraps this serializer so that `null` is a first-class value. A `null` serializes to [SettingsValue.NullValue] and
 * back, and the receiver handles any other value. So a key can declare a nullable type with a `null` default:
 * `SettingsKey<String?>("x", default = null, serializer = StringSerializer.nullable())`. The eager-default constructor cannot
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

  override fun deserialize(value: SettingsValue): List<T> = value.requireValue<ListValue>().items.map(element::deserialize)
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

  override fun deserialize(value: SettingsValue): Map<String, V> = value.requireValue<MapValue>().values.mapValues { valueSerializer.deserialize(it.value) }
}

/** Passes a [MapValue] through unchanged. A [SettingsMapKey] uses it, because its value is already a [MapValue]. */
@Experimental
object DumbMapValueSerializer : Serializer<MapValue> {
  override fun serialize(value: MapValue): SettingsValue = value
  override fun deserialize(value: SettingsValue): MapValue = value.requireValue<MapValue>()
}

/**
 * Returns a serializer that stores an entry of [E] by its constant name, [Enum.name], as a [StringValue], with no
 * conversion of the name (ADR 0178). A user edits the config by hand, so the stored value is readable. The constant
 * name is unique within [E], so two entries never share a stored value. A rename of a constant makes the stored value
 * unknown, and a read then falls back to the default.
 *
 * Use it for a key with no eager default, for example a nullable key or a key with a default factory:
 * `SettingsKey("x", serializer = enumSerializer<MyEnum>().nullable(), default = null)`. A key with an enum default needs
 * no serializer: the eager-default constructor of [SettingsKey] infers this one. It composes with [nullable], [listOf]
 * and [mapOf].
 */
@Experimental
inline fun <reified E : Enum<E>> enumSerializer(): Serializer<E> = enumSerializer(E::class.java)

/**
 * Returns the [enumSerializer] of [enumClass]. Use this form where the enum is known only as a class, for example in
 * Java code. The inference from a default value calls it too, because it has only the runtime class.
 */
@Experimental
fun <E : Enum<E>> enumSerializer(enumClass: Class<E>): Serializer<E> = EnumSerializer(enumClass)

private class EnumSerializer<E : Enum<E>>(private val enumClass: Class<E>) : Serializer<E> {
  override fun serialize(value: E): SettingsValue = StringValue(value.name)

  override fun deserialize(value: SettingsValue): E {
    val name = value.requireValue<StringValue>(enumClass.name).value
    return requireNotNull(enumClass.enumConstants.firstOrNull { it.name == name }) {
      "'$name' is not a constant of ${enumClass.name}"
    }
  }
}

/**
 * Returns the built-in serializer inferred from the runtime class of [default]: a scalar serializer, or the
 * [enumSerializer] of the enum of an entry. An entry with a body has a class of its own, so the enum comes from
 * [declaringJavaClass].
 *
 * Backs the eager-default constructor of [SettingsKey]: `SettingsKey<Int>("editor.indent.size", default = 4)`.
 *
 * @throws IllegalStateException when [default] is not an enum entry or one of the scalar types [Int], [Long],
 *   [Double], [Boolean] and [String]
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> inferSerializerFromDefault(default: T): Serializer<T> = when (default) {
  is Int -> IntSerializer as Serializer<T>
  is Long -> LongSerializer as Serializer<T>
  is Double -> DoubleSerializer as Serializer<T>
  is Boolean -> BooleanSerializer as Serializer<T>
  is String -> StringSerializer as Serializer<T>
  is Enum<*> -> enumSerializer(default.declaringJavaClass) as Serializer<T>
  else -> error("Cannot infer a serializer from default '$default'; pass one explicitly (only an enum and the scalars " +
                "Int/Long/Double/Boolean/String are inferred).")
}

/**
 * Returns the built-in serializer inferred from the static type [T], with its nullability. [inferSerializerFromDefault]
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
