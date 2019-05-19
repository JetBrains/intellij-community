// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.values

open class PrimitiveValue(type: ValueType, override val valueString: String) : ValueBase(type) {

  constructor(type: ValueType, value: Int) : this(type, Integer.toString(value)) {
  }

  constructor(type: ValueType, value: Long) : this(type, java.lang.Long.toString(value)) {
  }

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