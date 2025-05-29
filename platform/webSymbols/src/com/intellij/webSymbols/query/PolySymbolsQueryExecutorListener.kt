// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.util.messages.Topic

interface PolySymbolsQueryExecutorListener {

  fun beforeQuery(queryParams: WebSymbolsQueryParams)

  fun afterQuery(queryParams: WebSymbolsQueryParams)

  companion object {
    val TOPIC: Topic<PolySymbolsQueryExecutorListener> = Topic(PolySymbolsQueryExecutorListener::class.java)
  }

}