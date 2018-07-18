// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.values

import com.intellij.util.SmartList
import org.jetbrains.concurrency.*
import org.jetbrains.debugger.EvaluateContext
import org.jetbrains.debugger.Variable
import org.jetbrains.debugger.VariablesHost
import java.util.*

abstract class ObjectValueBase<VALUE_LOADER : ValueManager>(type: ValueType) : ValueBase(type), ObjectValue {
  protected abstract val childrenManager: VariablesHost<VALUE_LOADER>

  override val properties: Promise<List<Variable>>
    get() = childrenManager.get()

  internal abstract inner class MyObsolescentAsyncFunction<PARAM, RESULT>(private val obsolescent: Obsolescent) : ObsolescentFunction<PARAM, Promise<RESULT>> {
    override fun isObsolete() = obsolescent.isObsolete || childrenManager.valueManager.isObsolete
  }

  override fun getProperties(names: List<String>, evaluateContext: EvaluateContext, obsolescent: Obsolescent): Promise<List<Variable>> = properties
    .thenAsync(object : MyObsolescentAsyncFunction<List<Variable>, List<Variable>>(obsolescent) {
      override fun `fun`(variables: List<Variable>) = getSpecifiedProperties(variables, names, evaluateContext)
    })

  override val valueString: String? = null

  override fun getIndexedProperties(from: Int, to: Int, bucketThreshold: Int, consumer: IndexedVariablesConsumer, componentType: ValueType?): Promise<*> = rejectedPromise<Any?>()

  @Suppress("UNCHECKED_CAST")
  override val variablesHost: VariablesHost<ValueManager>
    get() = childrenManager as VariablesHost<ValueManager>
}

fun getSpecifiedProperties(variables: List<Variable>, names: List<String>, evaluateContext: EvaluateContext): Promise<List<Variable>> {
  val properties = SmartList<Variable>()
  var getterCount = 0
  for (property in variables) {
    if (!property.isReadable || !names.contains(property.name)) {
      continue
    }

    if (!properties.isEmpty()) {
      Collections.sort(properties) { o1, o2 -> names.indexOf(o1.name) - names.indexOf(o2.name) }
    }

    properties.add(property)
    if (property.value == null) {
      getterCount++
    }
  }

  if (getterCount == 0) {
    return resolvedPromise(properties)
  }
  else {
    val promises = SmartList<Promise<*>>()
    for (variable in properties) {
      if (variable.value == null) {
        val valueModifier = variable.valueModifier!!
        promises.add(valueModifier.evaluateGet(variable, evaluateContext))
      }
    }
    return promises.all(properties)
  }
}