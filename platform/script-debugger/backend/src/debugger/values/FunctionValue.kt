/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
