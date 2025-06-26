// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.polySymbols.PolySymbol

interface PolySymbolPrioritizedScope {

  val priority: PolySymbol.Priority?

}