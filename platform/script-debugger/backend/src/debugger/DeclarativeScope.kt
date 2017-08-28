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

import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.cancelledPromise
import org.jetbrains.debugger.values.ObjectValue
import org.jetbrains.debugger.values.ValueManager

abstract class DeclarativeScope<VALUE_MANAGER : ValueManager>(type: ScopeType, description: String? = null) : ScopeBase(type, description) {
  protected abstract val childrenManager: VariablesHost<VALUE_MANAGER>

  override val variablesHost: VariablesHost<*>
    get() = childrenManager

  protected fun loadScopeObjectProperties(value: ObjectValue): Promise<List<Variable>> {
    if (childrenManager.valueManager.isObsolete) {
      return cancelledPromise()
    }

    return value.properties.done { childrenManager.updateCacheStamp() }
  }
}