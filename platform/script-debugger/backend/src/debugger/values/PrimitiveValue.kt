// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger.values

open class PrimitiveValue(type: ValueType, override val valueString: String) : ValueBase(type) {

  constructor(type: ValueType, value: Int) : this(type, value.toString())

  constructor(type: ValueType, value: Long) : this(type, value.toString())

  companion object {
    const val NA_N_VALUE: String = "NaN"
    const val INFINITY_VALUE: String = "Infinity"

    @JvmField
    val NULL: PrimitiveValue = PrimitiveValue(ValueType.NULL, "null")
    @JvmField
    val UNDEFINED: PrimitiveValue = PrimitiveValue(ValueType.UNDEFINED, "undefined")

    val NAN: PrimitiveValue = PrimitiveValue(ValueType.NUMBER, NA_N_VALUE)
    val INFINITY: PrimitiveValue = PrimitiveValue(ValueType.NUMBER, INFINITY_VALUE)

    private val TRUE = PrimitiveValue(ValueType.BOOLEAN, "true")
    private val FALSE = PrimitiveValue(ValueType.BOOLEAN, "false")

    fun bool(value: String): PrimitiveValue {
      return if (value == "true") TRUE else FALSE
    }
  }
}