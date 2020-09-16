/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.java

import com.android.tools.idea.gradle.dsl.api.GradleFileModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelImpl
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement

private const val INDENT = 2
private const val RESET = 0
private const val LIGHT_BLUE = 94
private const val LIGHT_RED = 91
private const val LIGHT_MAGENTA = 95
private const val LIGHT_CYAN = 96
private const val LIGHT_YELLOW = 93
private fun color(code: Int): String {
  return "\u001B[${code}m"
}

fun GradleFileModel.print() {
  val element = GradleFileModelImpl::class.java.getDeclaredField("myGradleDslFile").let {
    it.isAccessible = true
    return@let it.get(this)
  }
  assert(element is GradleDslElement)
  (element as GradleDslElement).print()
}

fun GradleDslElement.print() {
  val builder = StringBuilder()
  printElement(builder, INDENT)
  println(builder.toString())
}

private val String.blue get() = if (isEmpty()) "" else color(LIGHT_BLUE) + this + color(RESET)
private val String.red get() = if (isEmpty()) "" else color(LIGHT_RED) + this + color(RESET)
private val String.magenta get() = if (isEmpty()) "" else color(LIGHT_MAGENTA) + this + color(RESET)
private val String.cyan get() = if (isEmpty()) "" else color(LIGHT_CYAN) + this + color(RESET)
private val String.yellow get() = if (isEmpty()) "" else color(LIGHT_YELLOW) + this + color(RESET)

private fun GradleDslElement.printElement(builder: StringBuilder, indent: Int) {
  when (this) {
    is GradleDslMethodCall -> {
      builder.append("${javaClass.simpleName.red} : ${name.magenta} ${methodName.yellow} : ${value.toString().cyan} ->\n")
      builder.append("${" ".repeat(indent)}| ${name.blue} -> ")
      argumentsElement.printElement(builder, indent + INDENT)
    }
    is GradleDslSimpleExpression -> builder.append("${javaClass.simpleName.red} : ${name.magenta} : ${value.toString().cyan}")
    is GradleDslExpressionList -> {
      builder.append("${javaClass.simpleName.red} : ${name.magenta} ->\n")
      expressions.forEachIndexed { i, e ->
        builder.append("${" ".repeat(indent)}${i} - ")
        e.printElement(builder, indent + INDENT)
        if (builder.last() != '\n') builder.append("\n")
      }
    }
    is GradlePropertiesDslElement -> {
      builder.append("${javaClass.simpleName.red} : ${name.magenta} ->\n")
      allElements.forEach {
        builder.append("${" ".repeat(indent)}| ${if (isApplied(it)) "[A]".yellow else ""} ${it.name.blue} -> ")
        it.printElement(builder, indent + INDENT)
        if (builder.last() != '\n') builder.append("\n")
      }
    }
    else -> {
      builder.append("${javaClass.simpleName.red} : ${name.magenta}\n")
    }
  }
}