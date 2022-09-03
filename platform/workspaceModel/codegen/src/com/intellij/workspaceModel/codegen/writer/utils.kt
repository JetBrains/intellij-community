package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.Field

fun <T> Collection<T>.lines(f: T.() -> String = { "$this" }): String =
  mapNotNull { f(it).takeIf { item -> item.isNotBlank() } }.joinToString("\n")

fun <T> Collection<T>.lines(indent: String, f: T.() -> String = { "$this" }): String =
  mapNotNull { f(it).takeIf { item -> item.isNotBlank() } }.indent(indent)

fun Collection<String>.indent(indent: String): String = when {
  size > 1 -> first().indentRestOnly(indent) + "\n" +
              drop(1).joinToString("\n") { it.indentAll(indent) }
  size == 1 -> single().indentRestOnly(indent)
  else -> joinToString("\n") { it.indentAll(indent) }
}

fun String.indentRestOnly(indent: String): String {
  val lines = lines()
  return when {
    lines.size > 1 -> lines.first() + "\n" + lines.drop(1).joinToString("\n") { "$indent$it" }
    lines.size == 1 -> lines.first()
    else -> ""
  }
}

private fun String.indentAll(indent: String): String {
  return lines().joinToString("\n") { "$indent$it" }
}

fun <T : Field<*, *>> Collection<T>.sum(
  indent: String,
  f: T.() -> String,
): String =
  buildString {
    val items = this@sum
    if (items.isEmpty()) append("0")
    else items.forEachIndexed { index, it ->
      if (index != 0) append(indent)
      append(f(it))
      if (index != items.size - 1) append(" + // ${it.name}\n")
      else append(" // ${it.name}")
    }
  }

fun <T : Field<*, *>> Collection<T>.and(
  indent: String,
  f: T.() -> String,
): String =
  buildString {
    val items = this@and
    if (items.isEmpty()) append("true")
    else items.forEachIndexed { index, it ->
      if (index != 0) append(indent)
      append(f(it))
      if (index != items.size - 1) append(" &&\n")
    }
  }

fun <T : Field<*, *>> Collection<T>.commaSeparated(
  indent: String,
  f: T.() -> String,
): String {
  var first = true
  return buildString {
    val items = this@commaSeparated
    items.forEach {
      val c = f(it)
      if (c.isNotEmpty()) {
        if (first) first = false
        else append(",\n$indent")
        append(c)
      }
    }
  }
}

fun override(flag: Boolean): String =
  if (flag) "override " else ""

fun unreachable(): Nothing = error("unreachable")

fun String.codeTemplate(): String {
  val lines = lines()
  val prefix = lines.last() + "    "
  return lines.dropLast(1).joinToString("\n") {
    it.removePrefix(prefix)
  }
}