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

  override fun getProperties(names: List<String>, evaluateContext: EvaluateContext, obsolescent: Obsolescent) = properties
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
    return all(promises, properties)
  }
}