package com.intellij.workspaceModel.codegen.impl.writer

fun <T> Collection<T>.lines(f: T.() -> String = { "$this" }): String =
  mapNotNull { f(it).takeIf { item -> item.isNotBlank() } }.joinToString("\n")

