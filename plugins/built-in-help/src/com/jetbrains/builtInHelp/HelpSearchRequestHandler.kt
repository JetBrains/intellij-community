// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import com.jetbrains.builtInHelp.search.HelpSearch

@Suppress("unused")
class HelpSearchRequestHandler : HelpProcessingRequestBase() {
  override val prefix: String = "/search/"

  override fun getProcessedData(query: String, maxHits: Int): String {
    return HelpSearch.search(query, maxHits)
  }
}