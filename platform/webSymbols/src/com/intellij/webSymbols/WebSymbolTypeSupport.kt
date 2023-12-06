// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

interface WebSymbolTypeSupport {

  fun resolve(types: List<TypeReference>): Any?

  data class TypeReference(
    val module: String?,
    val name: String
  )
}