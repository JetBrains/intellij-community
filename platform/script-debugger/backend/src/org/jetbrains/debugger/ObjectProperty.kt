package org.jetbrains.debugger

import org.jetbrains.debugger.values.FunctionValue

/**
 * Exposes additional data if variable is a property of object and its property descriptor
 * is available.
 */
interface ObjectProperty : Variable {
  val isWritable: Boolean

  val getter: FunctionValue?

  val setter: FunctionValue?


  val isConfigurable: Boolean

  val isEnumerable: Boolean
}
