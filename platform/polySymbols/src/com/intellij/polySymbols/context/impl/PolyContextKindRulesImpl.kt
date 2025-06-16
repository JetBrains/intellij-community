// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.context.impl

import com.intellij.polySymbols.PolyContextName
import com.intellij.polySymbols.context.PolyContextKindRules
import com.intellij.polySymbols.context.PolyContextKindRules.DisablementRules
import com.intellij.polySymbols.context.PolyContextKindRules.EnablementRules

internal data class PolyContextKindRulesImpl(
  override val enable: Map<PolyContextName, List<EnablementRules>>,
  override val disable: Map<PolyContextName, List<DisablementRules>>,
) : PolyContextKindRules
