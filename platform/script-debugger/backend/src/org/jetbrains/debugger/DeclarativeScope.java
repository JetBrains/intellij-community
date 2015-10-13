package org.jetbrains.debugger

import com.intellij.util.Consumer
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.values.ObjectValue
import org.jetbrains.debugger.values.ValueManager

abstract class DeclarativeScope<VALUE_MANAGER : ValueManager<out Vm>>(type: Scope.Type, description: String? = null) : ScopeBase(type, description) {
  protected abstract val childrenManager: VariablesHost<VALUE_MANAGER>

  protected fun loadScopeObjectProperties(value: ObjectValue): Promise<List<Variable>> {
    if (childrenManager.valueManager.isObsolete) {
      return ValueManager.reject()
    }

    return value.properties.done(object : Consumer<List<Variable>> {
      override fun consume(variables: List<Variable>) {
        childrenManager.updateCacheStamp()
      }
    })
  }

  override fun getVariablesHost() = childrenManager
}