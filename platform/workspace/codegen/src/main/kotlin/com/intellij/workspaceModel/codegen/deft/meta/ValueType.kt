// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.meta

/**
 * [ValueType] is used to describe the set of suitable values.
 */
sealed class ValueType<T> {
  sealed class Primitive<T> : ValueType<T>()

  /**
   * `true` or `false`. Actually, the shorthand for `OneOf(Const(true), Const(false))`.
   *
   * Same as Kotlin [kotlin.Boolean] and Java primitive `boolean` ([java.lang.Boolean])
   */
  object Boolean : Primitive<kotlin.Boolean>()

  /**
   * 8-bit signed integer with the least significant bit.
   *
   * Same as Kotlin [kotlin.Byte] and Java primitive `byte`([java.lang.Byte]).
   */
  object Byte : Primitive<kotlin.Byte>()

  /**
   * 16-bit signed, big endian integer with the least significant bit.
   *
   * Same as Kotlin [kotlin.Short] and Java primitive `short`([java.lang.Short]).
   */
  object Short : Primitive<kotlin.Short>()

  /**
   * 32-bit signed, big endian integer with the least significant bit.
   *
   * Same as Kotlin [kotlin.Int] and Java primitive `int`([java.lang.Integer])
   */
  object Int : Primitive<kotlin.Int>()

  /**
   * 64-bit signed, big endian integer with the least significant bit.
   *
   * Same as Kotlin [kotlin.Long] and Java primitive `long`([java.lang.Long])
   */
  object Long : Primitive<kotlin.Long>()

  /**
   * Represents a double-precision 32-bit [IEEE 754](https://en.wikipedia.org/wiki/IEEE_754) floating point number.
   *
   * Same as Kotlin [kotlin.Float] and Java primitive `float`([java.lang.Float])
   */
  object Float : Primitive<kotlin.Float>()

  /**
   * Represents a double-precision 64-bit [IEEE 754](https://en.wikipedia.org/wiki/IEEE_754) floating point number.
   *
   * Same as Kotlin [kotlin.Double] and Java primitive `double`([java.lang.Double])
   */
  object Double : Primitive<kotlin.Double>()

  /**
   * 8-bit unsigned integer.
   *
   * Same as Kotlin [UByte]. Not available in Java.
   */
  object UByte : Primitive<kotlin.UByte>()

  /**
   * 16-bit unsigned, big endian integer.
   *
   * Same as Kotlin [UShort]. Not available in Java.
   */
  object UShort : Primitive<kotlin.UShort>()

  /**
   * 32-bit unsigned, big endian integer.
   *
   * Same as Kotlin [UInt]. Not available in Java.
   */
  object UInt : Primitive<kotlin.UInt>()

  /**
   * 64-bit unsigned, big endian integer.
   *
   * Same as Kotlin [ULong]. Not available in Java.
   */
  object ULong : Primitive<kotlin.ULong>()

  /**
   * 16-bit Unicode character encoded by [UShort].
   *
   * Same as Kotlin [kotlin.Char] and Java [java.lang.Character]
   */
  object Char : Primitive<kotlin.Char>()

  /**
   * Behaves as [List] of [Char].
   *
   * Unlike all other [ValueType]s, requires decoding on read and encoding on write,
   * thus should be cached on client side. The actual binary representation is
   * implementation detail.
   *
   * Translated to [kotlin.String] and Java [java.lang.String].
   */
  object String : Primitive<kotlin.String>()

  /**
   * Polymorphic reference to [Obj] of [target] [ObjClass].
   **/
  data class ObjRef<T : Obj>(
    val child: kotlin.Boolean,
    val target: ObjClass<T>
  ) : ValueType<T>()

  sealed class JvmClass<T>(
    val javaClassName: kotlin.String,
    val javaSuperClasses: kotlin.collections.List<kotlin.String>,
  ) : ValueType<T>()

  class DataClass<T>(
    javaClassName: kotlin.String,
    javaSuperClasses: kotlin.collections.List<kotlin.String>,
    val properties: kotlin.collections.List<DataClassProperty>
  ) : JvmClass<T>(javaClassName, javaSuperClasses)

  data class DataClassProperty(val name: kotlin.String, val type: ValueType<*>)

  class SealedClass<T>(
    javaClassName: kotlin.String,
    javaSuperClasses: kotlin.collections.List<kotlin.String>,
    val subclasses: kotlin.collections.List<JvmClass<*>>
  ) : JvmClass<T>(javaClassName, javaSuperClasses)

  class Blob<T>(
    javaClassName: kotlin.String,
    javaSuperClasses: kotlin.collections.List<kotlin.String>,
  ) : JvmClass<T>(javaClassName, javaSuperClasses)

  class Enum<T>(
    javaClassName: kotlin.String,
  ) : JvmClass<T>(javaClassName, emptyList())

  class Object<T>(
    javaClassName: kotlin.String,
    javaSuperClasses: kotlin.collections.List<kotlin.String>,
  ) : JvmClass<T>(javaClassName, javaSuperClasses)

  /**
   * Tuple of fields with fixed [ValueType]s.
   *
   *  [Structure] is [ValueType] itself, thus can be nested (but without recursion)
   * and compared by value.
   *
   * Use [ObjClass] to define your own structure.
   *
   * Read "Values, References and Objects" in [Obj] to understand differences
   * between [ValueType] (which [Structure] is) and [ObjClass].
   *
   * Unlike [ObjProperty], [Structure] fields are not nominal.
   * Thus, two structures with same set of field types considered equal.
   */
  data class Structure<T>(val fields: kotlin.collections.List<ValueType<*>>) : ValueType<T>()

  sealed class Collection<E, T : kotlin.collections.Collection<E>> : ValueType<T>() {
    abstract val elementType: ValueType<E>
  }

  data class List<T>(override val elementType: ValueType<T>) : Collection<T, kotlin.collections.List<T>>()
  data class Set<T>(override val elementType: ValueType<T>) : Collection<T, kotlin.collections.Set<T>>()

  data class Map<K, V>(
    val keyType: ValueType<K>,
    val valueType: ValueType<V>
  ) : ValueType<Map<K, V>>()

  data class Optional<T>(val type: ValueType<T>) : ValueType<T?>()

  /**
   * Any possible value.
   * Valid inside [ValueRef] only.
   *
   * Logical representation: [AnyOf]`(...all value kinds)`.
   */
  object Any : ValueType<kotlin.Any>()

  /**
   * Nothing has no possible values.
   * Used to denote [Optional] values.
   *
   * Logical representation: [Both]`(...all value kinds, except Any)`.
   */
  object Nothing : ValueType<kotlin.Nothing>()
}
