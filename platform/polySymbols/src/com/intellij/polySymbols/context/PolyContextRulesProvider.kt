// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.PolyContextKind
import com.intellij.util.containers.MultiMap

interface PolyContextRulesProvider : ModificationTracker {

  fun getContextRules(): MultiMap<PolyContextKind, PolyContextKindRules>

  fun createPointer(): Pointer<out PolyContextRulesProvider>

}