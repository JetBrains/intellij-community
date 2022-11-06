// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

interface WebSymbolsQueryParams {

  val framework: String?
    get() = queryExecutor.framework

  val queryExecutor: WebSymbolsQueryExecutor

  val virtualSymbols: Boolean
}