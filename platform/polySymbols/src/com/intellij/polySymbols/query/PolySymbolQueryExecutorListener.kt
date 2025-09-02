// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.util.messages.Topic

interface PolySymbolQueryExecutorListener {

  fun beforeQuery(queryParams: PolySymbolQueryParams)

  fun afterQuery(queryParams: PolySymbolQueryParams)

  companion object {
    @JvmField
    val TOPIC: Topic<PolySymbolQueryExecutorListener> = Topic(PolySymbolQueryExecutorListener::class.java)
  }

}