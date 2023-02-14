// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context.impl

import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.context.WebSymbolsContextKindRules.DisablementRules
import com.intellij.webSymbols.context.WebSymbolsContextKindRules.EnablementRules

internal data class WebSymbolsContextKindRulesImpl(override val enable: Map<ContextName, List<EnablementRules>>,
                                                   override val disable: Map<ContextName, List<DisablementRules>>): WebSymbolsContextKindRules
