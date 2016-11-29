package org.jetbrains.debugger.values

import org.jetbrains.debugger.Variable

abstract class IndexedVariablesConsumer {
  // null if array is not sparse
  abstract fun consumeRanges(ranges: IntArray?)

  abstract fun consumeVariables(variables: List<Variable>)

  open val isObsolete: Boolean
    get() = false
}