// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.util

import java.lang.IllegalArgumentException
import kotlin.reflect.KMutableProperty0

interface SettingMapping<External> {
  /**
   * @throws IllegalStateException if [isExportAllowed] == false
   */
  fun export(): External

  /**
   * @throws IllegalStateException if [isImportAllowed] == false
   * @throws UnexpectedIncomingValue if [value] could not be converted anywhere in the chain
   */
  fun import(value: External)
  val isExportAllowed
    get() = true

  val isImportAllowed
    get() = true
}

fun <E> SettingMapping<E>.importIfAllowed(value: E) {
  if (isImportAllowed) import(value)
}

abstract class WrappingSettingMapping<Outer, Inner>(val wrappee: SettingMapping<Inner>) : SettingMapping<Outer> {
  override val isExportAllowed by wrappee::isExportAllowed

  override val isImportAllowed by wrappee::isImportAllowed
}

class ConvertingSettingMapping<External, Internal>(
  wrappee: SettingMapping<Internal>,
  private val convertor: Convertor<External, Internal>
) : WrappingSettingMapping<External, Internal>(wrappee) {
  override fun export(): External = convertor.convertOutgoing(wrappee.export())

  override fun import(value: External) = wrappee.import(convertor.convertIncoming(value))
}

class ConstSettingMapping<E>(val value: E) : SettingMapping<E> {
  override fun export(): E = value
  override fun import(value: E) { throw IllegalStateException("const setting mapping cannot import anything") }
  override val isImportAllowed = false
}

interface Convertor<E, I> {
  fun convertIncoming(value: E): I
  fun convertOutgoing(value: I): E
}

/**
 * Signalizes that a value could not be parsed correctly on import.
 */
class UnexpectedIncomingValue(val value: Any) : IllegalArgumentException(value.toString())

object IntConvertor : Convertor<String, Int> {
  override fun convertOutgoing(value: Int): String = value.toString()
  override fun convertIncoming(value: String): Int = try {
    value.toInt()
  }
  catch (e: NumberFormatException) {
    throw UnexpectedIncomingValue(value)
  }
}

object BooleanConvertor : Convertor<String, Boolean> {
  override fun convertOutgoing(value: Boolean): String = value.toString()
  override fun convertIncoming(value: String): Boolean = when (value.lowercase()) {
    "true" -> true
    "false" -> false
    else -> throw UnexpectedIncomingValue(value)
  }
}

class FieldSettingMapping<I>(
  val field: KMutableProperty0<I>,
) : SettingMapping<I> {
  override fun export(): I = field.getter.call()
  override fun import(value: I) = field.setter.call(value)
}

class AlsoImportFieldsSettingMapping<I>(
  wrappee: SettingMapping<I>,
  val fields: Array<out KMutableProperty0<I>>
) : WrappingSettingMapping<I, I>(wrappee) {
  override fun export(): I = wrappee.export()

  override fun import(value: I) {
    fields.forEach { it.setter.call(value) }
    wrappee.import(value)
  }
}

class ComputableSettingMapping<I>(
  val _import: (I) -> Unit,
  val _export: () -> I
) : SettingMapping<I> {
  override fun export() = _export()
  override fun import(value: I) = _import(value)
}

class ConditionalImportSettingMapping<External>(
  wrappee: SettingMapping<External>,
  override val isImportAllowed: Boolean
) : WrappingSettingMapping<External, External>(wrappee) {
  override fun export(): External = wrappee.export()

  override fun import(value: External) {
    if (isImportAllowed) {
      wrappee.import(value)
    }
    else {
      throw IllegalStateException()
    }
  }
}

object SettingsMappingHelpers {
  /**
   * Create export only mapping.
   *
   * Meant for cases when there is no corresponding IDEA settings field (the behavior is *constant*).
   * I.e. for Eclipse options, that do not have a counterpart in IDEA,
   * but the default behaviour in IDEA corresponds to one of the possible values for this Eclipse option.
   */
  fun <I> const(value: I) = ConstSettingMapping(value)

  /**
   * Create mapping to a **bound** property.
   *
   * Note that the property has to be public, so that the mapping object can call its getter/setter
   */
  fun <T> field(field: KMutableProperty0<T>) = FieldSettingMapping(field)

  fun <I> compute(import: (I) -> Unit, export: () -> I) = ComputableSettingMapping(import, export)

  /**
   * Does not export nor import any setting.
   *
   * It is used to explicitly declare that an Eclipse option is not mapped to anything.
   */
  fun ignored() = IgnoredSettingMapping

}

fun <External, Internal> SettingMapping<Internal>.convert(convertor: Convertor<External, Internal>) =
  ConvertingSettingMapping(this, convertor)

fun SettingMapping<Boolean>.convertBoolean() = convert(BooleanConvertor)

fun SettingMapping<Int>.convertInt() = convert(IntConvertor)

/**
 * Specifies that a [FieldSettingMapping] should only be used for export.
 *
 * Useful for N to 1 (external to internal) mappings.
 * I.e. when multiple options in Eclipse correspond to a single IDEA setting (field).
 * This function allows us to control which one of the Eclipse options will be used to set the IDEA setting,
 * otherwise, the result depends on the order, in which settings are imported.
 */
fun <External> SettingMapping<External>.doNotImport() =
  ConditionalImportSettingMapping(this, false)

/**
 * Specify fields whose values should be set alongside a [FieldSettingMapping] on import.
 *
 * Useful 1 to N (external to internal) mappings.
 * I.e. when setting one option in Eclipse corresponds to setting multiple settings in IDEA.
 * Only the original ([FieldSettingMapping]) will be used for export.
 */
fun <Internal> FieldSettingMapping<Internal>.alsoSet(vararg fields: KMutableProperty0<Internal>) =
  AlsoImportFieldsSettingMapping(this, fields)

class InvertingBooleanSettingMapping(wrappee: SettingMapping<Boolean>)
  : WrappingSettingMapping<Boolean, Boolean>(wrappee) {
  override fun export(): Boolean = !wrappee.export()

  override fun import(value: Boolean) = wrappee.import(!value)
}

fun SettingMapping<Boolean>.invert() = InvertingBooleanSettingMapping(this)

object IgnoredSettingMapping : SettingMapping<String> {
  override fun export(): String {
    throw IllegalStateException()
  }

  override fun import(value: String) {
    throw IllegalStateException()
  }

  override val isExportAllowed = false

  override val isImportAllowed = false

}