// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder


internal class StringInterner {
  private val set = HashMap<String, String>() // todo use a more compact collection?
  fun intern(name: String): String = set.getOrPut(name) { name }
}