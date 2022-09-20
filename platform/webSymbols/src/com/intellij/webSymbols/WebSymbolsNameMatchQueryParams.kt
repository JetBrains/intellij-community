// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

data class WebSymbolsNameMatchQueryParams(override val registry: WebSymbolsRegistry,
                                          override val virtualSymbols: Boolean = true,
                                          val abstractSymbols: Boolean = false,
                                          val strictScope: Boolean = false) : WebSymbolsRegistryQueryParams