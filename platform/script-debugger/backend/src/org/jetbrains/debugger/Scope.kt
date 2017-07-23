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
package org.jetbrains.debugger

import org.jetbrains.debugger.values.ObjectValue

enum class ScopeType {
  GLOBAL,
  LOCAL,
  WITH,
  CLOSURE,
  CATCH,
  LIBRARY,
  CLASS,
  INSTANCE,
  BLOCK,
  SCRIPT,
  UNKNOWN
}

interface Scope {
  val type: ScopeType

  /**
   * Class or function or file name
   */
  val description: String?

  val variablesHost: VariablesHost<*>

  val isGlobal: Boolean
}

abstract class ScopeBase(override val type: ScopeType, override val description: String?) : Scope {
  override val isGlobal: Boolean
    get() = type === ScopeType.GLOBAL || type === ScopeType.LIBRARY
}

class ObjectScope(type: ScopeType, private val value: ObjectValue) : ScopeBase(type, value.valueString), Scope {
  override val variablesHost: VariablesHost<*>
    get() = value.variablesHost
}