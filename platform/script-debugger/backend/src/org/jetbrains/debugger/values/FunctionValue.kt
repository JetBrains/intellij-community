package org.jetbrains.debugger.values

import com.intellij.util.ThreeState
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.Scope

interface FunctionValue : ObjectValue {
  /**
   * You must invoke [.resolve] to use any function value methods
   */
  fun resolve(): Promise<FunctionValue>

  /**
   * Returns position of opening parenthesis of function arguments. Position is absolute
   * within resource (not relative to script start position).

   * @return position or null if position is not available
   */
  val openParenLine: Int

  val openParenColumn: Int

  val scopes: Array<Scope>?

  /**
   * Method could be called (it is normal and expected) for unresolved function.
   * It must return quickly. Return [com.intellij.util.ThreeState.UNSURE] otherwise.
   */
  fun hasScopes() = ThreeState.UNSURE
}
