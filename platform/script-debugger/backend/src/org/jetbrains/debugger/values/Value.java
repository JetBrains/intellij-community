package org.jetbrains.debugger.values

/**
 * An object that represents a VM variable value (compound or atomic).
 */
interface Value {
  val type: ValueType

  /**
   * @return a string representation of this value
   */
  val valueString: String?
}
