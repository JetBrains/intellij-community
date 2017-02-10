package org.jetbrains.debugger.values

private val VALUE_TYPES = ValueType.values()

/**
 * Don't forget to update NashornDebuggerSupport.ValueType and DebuggerSupport.ts respectively also
 */
enum class ValueType {
  OBJECT,
  NUMBER,
  STRING,
  FUNCTION,
  BOOLEAN,

  ARRAY,
  NODE,

  UNDEFINED,
  NULL,
  SYMBOL;

  /**
   * Returns whether `type` corresponds to a JsObject. Note that while 'null' is an object
   * in JavaScript world, here for API consistency it has bogus type [.NULL] and is
   * not a [ObjectValue]
   */
  val isObjectType: Boolean
    get() = this == OBJECT || this == ARRAY || this == FUNCTION || this == NODE

  companion object {
    fun fromIndex(index: Int) = VALUE_TYPES.get(index)
  }
}