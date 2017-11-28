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
import org.jetbrains.concurrency.thenAsyncAccept
import org.jetbrains.debugger.values.Value
import org.jetbrains.io.JsonUtil
import java.util.*
import java.util.regex.Pattern

private val KEY_NOTATION_PROPERTY_NAME_PATTERN = Pattern.compile("[\\p{L}_$]+[\\d\\p{L}_$]*")

object ValueModifierUtil {
  fun setValue(variable: Variable,
               newValue: String,
               evaluateContext: EvaluateContext,
               modifier: ValueModifier) = evaluateContext.evaluate(newValue)
    .thenAsyncAccept { modifier.setValue(variable, it.value, evaluateContext) }

  fun evaluateGet(variable: Variable,
                  host: Any,
                  evaluateContext: EvaluateContext,
                  selfName: String): Promise<Value> {
    val builder = StringBuilder(selfName)
    appendUnquotedName(builder, variable.name)
    return evaluateContext.evaluate(builder.toString(), Collections.singletonMap(selfName, host), false)
      .then {
        variable.value = it.value
        it.value
      }
  }

  fun propertyNamesToString(list: List<String>, quotedAware: Boolean): String {
    val builder = StringBuilder()
    for (i in list.indices.reversed()) {
      val name = list[i]
      doAppendName(builder, name, quotedAware && (name[0] == '"' || name[0] == '\''))
    }
    return builder.toString()
  }

  fun appendUnquotedName(builder: StringBuilder, name: String) {
    doAppendName(builder, name, false)
  }
}

private fun doAppendName(builder: StringBuilder, name: String, quoted: Boolean) {
  val isProperty = !builder.isEmpty()
  if (isProperty) {
    val useKeyNotation = !quoted && KEY_NOTATION_PROPERTY_NAME_PATTERN.matcher(name).matches()
    if (useKeyNotation) {
      builder.append('.').append(name)
    }
    else {
      builder.append('[')
      if (quoted) builder.append(name) 
      else JsonUtil.escape(name, builder)
      builder.append(']')
    }
  }
  else {
    builder.append(name)
  }
}