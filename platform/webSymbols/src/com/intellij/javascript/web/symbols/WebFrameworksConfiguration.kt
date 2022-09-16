package com.intellij.javascript.web.symbols

import com.intellij.model.Pointer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface WebFrameworksConfiguration : WebSymbolNameConversionRules {

  val enableWhen: Map<FrameworkId, List<EnablementRules>>
  val disableWhen: Map<FrameworkId, List<DisablementRules>>

  override fun createPointer(): Pointer<out WebFrameworksConfiguration>

  data class DisablementRules(val fileExtensions: List<String>,
                              val fileNamePatterns: List<Regex>)

  data class EnablementRules(val nodePackages: List<String>,
                             val fileExtensions: List<String>,
                             val ideLibraries: List<String>,
                             val fileNamePatterns: List<Regex>,
                             val scriptUrlPatterns: List<Regex>)

}
