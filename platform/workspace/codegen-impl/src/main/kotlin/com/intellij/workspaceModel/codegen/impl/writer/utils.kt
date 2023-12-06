package com.intellij.workspaceModel.codegen.impl.writer

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

