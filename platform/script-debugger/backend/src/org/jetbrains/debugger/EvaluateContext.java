package org.jetbrains.debugger

import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.values.Value

class EvaluateResult(val value: Value, val wasThrown: Boolean = false)

/**
 * A context in which watch expressions may be evaluated. Typically corresponds to stack frame
 * of suspended process, but may also be detached from any stack frame
 */
interface EvaluateContext {
  /**
   * Evaluates an arbitrary `expression` in the particular context.
   * Previously loaded [org.jetbrains.debugger.values.ObjectValue]s can be addressed from the expression if listed in
   * additionalContext parameter.
   */
  fun evaluate(expression: String, additionalContext: Map<String, Any>? = null, enableBreak: Boolean = false): Promise<EvaluateResult>

  /**
   * optional to implement, some protocols, WIP for example, require you to release remote objects
   */
  fun withValueManager(objectGroup: String): EvaluateContext

  /**
   * If you evaluate "foo.bar = 4" and want to update Variables view (and all other clients), you can use use this task
   * @param promise
   */
  fun refreshOnDone(promise: Promise<*>): Promise<*>

  /**
   * call only if withLoader was called before
   */
  fun releaseObjects()
}