package org.jetbrains.debugger.values

import com.intellij.util.ThreeState
import org.jetbrains.concurrency.Obsolescent
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.EvaluateContext
import org.jetbrains.debugger.Variable
import org.jetbrains.debugger.VariablesHost
import org.jetbrains.debugger.Vm

/**
 * A compound value that has zero or more properties
 */
interface ObjectValue : Value {
  val className: String?

  val properties: Promise<List<Variable>>

  fun getProperties(names: List<String>, evaluateContext: EvaluateContext, obsolescent: Obsolescent): Promise<List<Variable>>

  val variablesHost: VariablesHost<ValueManager<Vm>>

  /**
   * from (inclusive) to (exclusive) ranges of array elements or elements if less than bucketThreshold

   * "to" could be -1 (sometimes length is unknown, so, you can pass -1 instead of actual elements size)
   */
  fun getIndexedProperties(from: Int, to: Int, bucketThreshold: Int, consumer: IndexedVariablesConsumer, componentType: ValueType?): Promise<*>

  /**
   * It must return quickly. Return [com.intellij.util.ThreeState.UNSURE] otherwise.
   */
  fun hasProperties() = ThreeState.UNSURE

  /**
   * It must return quickly. Return [com.intellij.util.ThreeState.UNSURE] otherwise.
   */
  fun hasIndexedProperties() = ThreeState.NO
}