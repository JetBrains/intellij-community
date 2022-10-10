// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.containers.MultiMap
import com.intellij.webSymbols.ContextKind

interface WebSymbolsContextRulesProvider: ModificationTracker {

  fun getContextRules(): MultiMap<ContextKind, WebSymbolsContextKindRules>

  fun createPointer(): Pointer<out WebSymbolsContextRulesProvider>

}