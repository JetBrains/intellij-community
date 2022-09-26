// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.framework

import com.intellij.model.Pointer
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.WebSymbolNameConversionRules

interface WebFrameworksConfiguration : WebSymbolNameConversionRules {

  val enableWhen: Map<FrameworkId, List<EnablementRules>>
  val disableWhen: Map<FrameworkId, List<DisablementRules>>

  override fun createPointer(): Pointer<out WebFrameworksConfiguration>

  data class DisablementRules(val fileExtensions: List<String>,
                              val fileNamePatterns: List<Regex>)

  data class EnablementRules(val pkgManagerDependencies: List<String>,
                             val fileExtensions: List<String>,
                             val ideLibraries: List<String>,
                             val fileNamePatterns: List<Regex>,
                             val scriptUrlPatterns: List<Regex>)

}
