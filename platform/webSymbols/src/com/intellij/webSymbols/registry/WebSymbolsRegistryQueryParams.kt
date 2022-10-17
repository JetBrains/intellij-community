// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.registry

interface WebSymbolsRegistryQueryParams {

  val framework: String?
    get() = registry.framework

  val registry: WebSymbolsRegistry

  val virtualSymbols: Boolean
}