package org.jetbrains.debugger

import com.intellij.util.Function
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
    return evaluateContext.evaluate(builder.toString(), Collections.singletonMap(selfName, host), false).then(object : Function<EvaluateResult, Value> {
      override fun `fun`(result: EvaluateResult): Value {
        variable.value = result.value
        return result.value
      }
    })
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
  val useKeyNotation = !quoted && KEY_NOTATION_PROPERTY_NAME_PATTERN.matcher(name).matches()
  if (builder.length != 0) {
    builder.append(if (useKeyNotation) '.' else '[')
  }
  if (useKeyNotation) {
    builder.append(name)
  }
  else {
    if (quoted) {
      builder.append(name)
    }
    else {
      JsonUtil.escape(name, builder)
    }
    builder.append(']')
  }
}