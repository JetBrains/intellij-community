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

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import org.jetbrains.concurrency.Obsolescent
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.then
import org.jetbrains.concurrency.thenAsync
import org.jetbrains.debugger.values.ValueType
import java.util.*
import java.util.regex.Pattern

private val UNNAMED_FUNCTION_PATTERN = Pattern.compile("^function[\\t ]*\\(")

private val NATURAL_NAME_COMPARATOR = Comparator<Variable> { o1, o2 -> naturalCompare(o1.name, o2.name) }

// start properties loading to achieve, possibly, parallel execution (properties loading & member filter computation)
fun processVariables(context: VariableContext,
                     variables: Promise<List<Variable>>,
                     obsolescent: Obsolescent,
                     consumer: (memberFilter: MemberFilter, variables: List<Variable>) -> Unit): Promise<Unit> {
  return context.memberFilter
    .thenAsync(obsolescent) { memberFilter ->
      variables
        .then(obsolescent) {
          consumer(memberFilter, it)
        }
    }
}

fun processScopeVariables(scope: Scope,
                          node: XCompositeNode,
                          context: VariableContext,
                          isLast: Boolean): Promise<Unit> {
  return processVariables(context, scope.variablesHost.get(), node) { memberFilter, variables ->
    val additionalVariables = memberFilter.additionalVariables

    val exceptionValue = context.vm?.suspendContextManager?.context?.exceptionData?.exceptionValue
    val properties = ArrayList<Variable>(variables.size + additionalVariables.size + (if (exceptionValue == null) 0 else 1))

    exceptionValue?.let {
      properties.add(VariableImpl("Exception", it))
    }

    val functions = SmartList<Variable>()
    for (variable in variables) {
      if (memberFilter.isMemberVisible(variable)) {
        val value = variable.value
        if (value != null && value.type == ValueType.FUNCTION && value.valueString != null && !UNNAMED_FUNCTION_PATTERN.matcher(
          value.valueString).lookingAt()) {
          functions.add(variable)
        }
        else {
          properties.add(variable)
        }
      }
    }

    addAditionalVariables(additionalVariables, properties, memberFilter)

    val comparator = if (memberFilter.hasNameMappings()) Comparator { o1, o2 ->
      naturalCompare(memberFilter.rawNameToSource(o1), memberFilter.rawNameToSource(o2))
    }
    else NATURAL_NAME_COMPARATOR
    properties.sortWith(comparator)
    functions.sortWith(comparator)

    if (!properties.isEmpty()) {
      node.addChildren(createVariablesList(properties, context, memberFilter), functions.isEmpty() && isLast)
    }

    if (!functions.isEmpty()) {
      node.addChildren(XValueChildrenList.bottomGroup(VariablesGroup("Functions", functions, context)), isLast)
    }
    else if (isLast && properties.isEmpty()) {
      node.addChildren(XValueChildrenList.EMPTY, true)
    }
  }
}

fun processNamedObjectProperties(variables: List<Variable>,
                                 node: XCompositeNode,
                                 context: VariableContext,
                                 memberFilter: MemberFilter,
                                 maxChildrenToAdd: Int,
                                 defaultIsLast: Boolean): List<Variable>? {
  val list = filterAndSort(variables, memberFilter)
  if (list.isEmpty()) {
    if (defaultIsLast) {
      node.addChildren(XValueChildrenList.EMPTY, true)
    }
    return null
  }

  val to = Math.min(maxChildrenToAdd, list.size)
  val isLast = to == list.size
  node.addChildren(createVariablesList(list, 0, to, context, memberFilter), defaultIsLast && isLast)
  if (isLast) {
    return null
  }
  else {
    node.tooManyChildren(list.size - to)
    return list
  }
}

fun filterAndSort(variables: List<Variable>, memberFilter: MemberFilter): List<Variable> {
  if (variables.isEmpty()) {
    return emptyList()
  }

  val additionalVariables = memberFilter.additionalVariables
  val result = ArrayList<Variable>(variables.size + additionalVariables.size)
  for (variable in variables) {
    if (memberFilter.isMemberVisible(variable)) {
      result.add(variable)
    }
  }
  result.sortWith(NATURAL_NAME_COMPARATOR)

  addAditionalVariables(additionalVariables, result, memberFilter)
  return result
}

private fun addAditionalVariables(additionalVariables: Collection<Variable>,
                                  result: MutableList<Variable>,
                                  memberFilter: MemberFilter,
                                  functions: MutableList<Variable>? = null) {
  val oldSize = result.size
  ol@ for (variable in additionalVariables) {
    for (i in 0..(oldSize - 1)) {
      val vmVariable = result[i]
      if (memberFilter.rawNameToSource(vmVariable) == memberFilter.rawNameToSource(variable)) {
        // we prefer additionalVariable here because it is more smart variable (e.g. NavigatableVariable)
        val vmValue = vmVariable.value
        // to avoid evaluation, use vm value directly
        if (vmValue != null && variable.value == null) {
          variable.value = vmValue
        }

        result.set(i, variable)
        continue@ol
      }
    }

    if (functions != null) {
      for (function in functions) {
        if (memberFilter.rawNameToSource(function) == memberFilter.rawNameToSource(variable)) {
          continue@ol
        }
      }
    }

    result.add(variable)
  }
}

// prefixed '_' must be last, fixed case sensitive natural compare
private fun naturalCompare(string1: String?, string2: String?): Int {
  //noinspection StringEquality
  if (string1 === string2) {
    return 0
  }
  if (string1 == null) {
    return -1
  }
  if (string2 == null) {
    return 1
  }

  val string1Length = string1.length
  val string2Length = string2.length
  var i = 0
  var j = 0
  while (i < string1Length && j < string2Length) {
    var ch1 = string1[i]
    var ch2 = string2[j]
    if ((StringUtil.isDecimalDigit(ch1) || ch1 == ' ') && (StringUtil.isDecimalDigit(ch2) || ch2 == ' ')) {
      var startNum1 = i
      while (ch1 == ' ' || ch1 == '0') {
        // skip leading spaces and zeros
        startNum1++
        if (startNum1 >= string1Length) {
          break
        }
        ch1 = string1[startNum1]
      }
      var startNum2 = j
      while (ch2 == ' ' || ch2 == '0') {
        // skip leading spaces and zeros
        startNum2++
        if (startNum2 >= string2Length) {
          break
        }
        ch2 = string2[startNum2]
      }
      i = startNum1
      j = startNum2
      // find end index of number
      while (i < string1Length && StringUtil.isDecimalDigit(string1[i])) {
        i++
      }
      while (j < string2Length && StringUtil.isDecimalDigit(string2[j])) {
        j++
      }
      val lengthDiff = (i - startNum1) - (j - startNum2)
      if (lengthDiff != 0) {
        // numbers with more digits are always greater than shorter numbers
        return lengthDiff
      }
      while (startNum1 < i) {
        // compare numbers with equal digit count
        val diff = string1[startNum1] - string2[startNum2]
        if (diff != 0) {
          return diff
        }
        startNum1++
        startNum2++
      }
      i--
      j--
    }
    else if (ch1 != ch2) {
      when {
        ch1 == '_' -> return 1
        ch2 == '_' -> return -1
        else -> return ch1 - ch2
      }
    }
    i++
    j++
  }
  // After the loop the end of one of the strings might not have been reached, if the other
  // string ends with a number and the strings are equal until the end of that number. When
  // there are more characters in the string, then it is greater.
  if (i < string1Length) {
    return 1
  }
  else if (j < string2Length) {
    return -1
  }
  return string1Length - string2Length
}

@JvmOverloads fun createVariablesList(variables: List<Variable>, variableContext: VariableContext, memberFilter: MemberFilter? = null): XValueChildrenList {
  return createVariablesList(variables, 0, variables.size, variableContext, memberFilter)
}

fun createVariablesList(variables: List<Variable>, from: Int, to: Int, variableContext: VariableContext, memberFilter: MemberFilter?): XValueChildrenList {
  val list = XValueChildrenList(to - from)
  var getterOrSetterContext: VariableContext? = null
  for (i in from until to) {
    val variable = variables[i]
    val normalizedName = memberFilter?.rawNameToSource(variable) ?: variable.name
    list.add(VariableView(normalizedName, variable, variableContext))
    if (variable is ObjectProperty) {
      if (variable.getter != null) {
        if (getterOrSetterContext == null) {
          getterOrSetterContext = NonWatchableVariableContext(variableContext)
        }
        list.add(VariableView(VariableImpl("get $normalizedName", variable.getter!!), getterOrSetterContext))
      }
      if (variable.setter != null) {
        if (getterOrSetterContext == null) {
          getterOrSetterContext = NonWatchableVariableContext(variableContext)
        }
        list.add(VariableView(VariableImpl("set $normalizedName", variable.setter!!), getterOrSetterContext))
      }
    }
  }
  return list
}

private class NonWatchableVariableContext(variableContext: VariableContext) : VariableContextWrapper(variableContext, null) {
  override fun watchableAsEvaluationExpression() = false
}