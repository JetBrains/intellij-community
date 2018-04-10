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
import org.jetbrains.debugger.values.Value

interface ValueModifier {
  // expression can contains reference to another variables in current scope, so, we should evaluate it before set
  // https://youtrack.jetbrains.com/issue/WEB-2342#comment=27-512122

  // we don't worry about performance in case of simple primitive values - boolean/string/numbers,
  // it works quickly and we don't want to complicate our code and debugger SDK
  fun setValue(variable: Variable, newValue: String, evaluateContext: EvaluateContext): Promise<*>

  fun setValue(variable: Variable, newValue: Value, evaluateContext: EvaluateContext): Promise<*>

  fun evaluateGet(variable: Variable, evaluateContext: EvaluateContext): Promise<Value>
}