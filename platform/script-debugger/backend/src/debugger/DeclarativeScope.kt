// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    return value.properties.onSuccess { childrenManager.updateCacheStamp() }
  }
}