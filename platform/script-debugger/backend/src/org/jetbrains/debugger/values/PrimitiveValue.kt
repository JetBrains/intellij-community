package org.jetbrains.debugger.values

open class PrimitiveValue(type: ValueType, override val valueString: String) : ValueBase(type) {

  constructor(type: ValueType, value: Int) : this(type, Integer.toString(value)) {
  }

  constructor(type: ValueType, value: Long) : this(type, java.lang.Long.toString(value)) {
  }

  companion object {
    val NA_N_VALUE = "NaN"
    val INFINITY_VALUE = "Infinity"

    @JvmField
    val NULL = PrimitiveValue(ValueType.NULL, "null")
    @JvmField
    val UNDEFINED = PrimitiveValue(ValueType.UNDEFINED, "undefined")

    val NAN = PrimitiveValue(ValueType.NUMBER, NA_N_VALUE)
    val INFINITY = PrimitiveValue(ValueType.NUMBER, INFINITY_VALUE)

    private val TRUE = PrimitiveValue(ValueType.BOOLEAN, "true")
    private val FALSE = PrimitiveValue(ValueType.BOOLEAN, "false")

    fun bool(value: String): PrimitiveValue {
      return if (value == "true") TRUE else FALSE
    }
  }
}