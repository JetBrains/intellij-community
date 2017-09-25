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
import org.jetbrains.concurrency.Obsolescent
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.EvaluateContext
import org.jetbrains.debugger.Variable
import org.jetbrains.debugger.VariablesHost

/**
 * A compound value that has zero or more properties
 */
interface ObjectValue : Value {
  val className: String?

  val properties: Promise<List<Variable>>

  fun getProperties(names: List<String>, evaluateContext: EvaluateContext, obsolescent: Obsolescent): Promise<List<Variable>>

  val variablesHost: VariablesHost<ValueManager>

  /**
   * from (inclusive) to (exclusive) ranges of array elements or elements if less than bucketThreshold

   * "to" could be -1 (sometimes length is unknown, so, you can pass -1 instead of actual elements size)
   */
  fun getIndexedProperties(from: Int, to: Int, bucketThreshold: Int, consumer: IndexedVariablesConsumer, componentType: ValueType? = null): Promise<*>

  /**
   * It must return quickly. Return [com.intellij.util.ThreeState.UNSURE] otherwise.
   */
  fun hasProperties() = ThreeState.UNSURE

  /**
   * It must return quickly. Return [com.intellij.util.ThreeState.UNSURE] otherwise.
   */
  fun hasIndexedProperties() = ThreeState.NO
}