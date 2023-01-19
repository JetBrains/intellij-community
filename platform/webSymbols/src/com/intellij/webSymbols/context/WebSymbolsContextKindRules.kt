// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context

import com.intellij.model.Pointer
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.context.impl.WebSymbolsContextKindRulesImpl

interface WebSymbolsContextKindRules {

  val enable: Map<ContextName, List<EnablementRules>>
  val disable: Map<ContextName, List<DisablementRules>>

  data class DisablementRules(val fileExtensions: List<String>,
                              val fileNamePatterns: List<Regex>)

  data class EnablementRules(val pkgManagerDependencies: List<String>,
                             val fileExtensions: List<String>,
                             val ideLibraries: List<String>,
                             val fileNamePatterns: List<Regex>,
                             val scriptUrlPatterns: List<Regex>)

  companion object {

    @JvmStatic
    fun create(enable: Map<ContextName, List<EnablementRules>>,
               disable: Map<ContextName, List<DisablementRules>>): WebSymbolsContextKindRules =
      WebSymbolsContextKindRulesImpl(enable, disable)

  }

}
