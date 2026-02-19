// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleGroovyUtil")

package org.jetbrains.plugins.gradle.service.execution

fun <K, V> Map<K, V>.toGroovyMapLiteral(mapKey: K.() -> String, mapValue: V.() -> String): String {
  if (isEmpty()) {
    return "[:]"
  }
  return "[" + entries.joinToString(",") { it.key.mapKey() + ":" + it.value.mapValue() } + "]"
}

fun <T> Collection<T>.toGroovyListLiteral(map: T.() -> String): String {
  return "[" + joinToString(",", transform = map) + "]"
}

fun String.toGroovyStringLiteral(): String {
  return replace("\\", "\\\\")
    .replace("'", "\\'")
    .let { "'$it'" }
}
