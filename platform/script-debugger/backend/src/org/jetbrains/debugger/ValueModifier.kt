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