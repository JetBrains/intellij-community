package com.intellij.cce.callGraphs

import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface CallGraphBuilder {
  val supportedLanguages: List<Language>

  companion object {
    val EP_NAME: ExtensionPointName<CallGraphBuilder> = ExtensionPointName.create("com.intellij.cce.callGraphBuilder")

    fun getForLanguages(languages: List<Language>): CallGraphBuilder {
      val suitableGraphBuilders = EP_NAME.extensionList.filter {
        it.supportedLanguages.sorted() == languages.sorted()
      }
      if (suitableGraphBuilders.isEmpty()) {
        throw IllegalStateException("No suitable graph builder found for languages $languages")
      }
      if (suitableGraphBuilders.size > 1) {
        throw IllegalStateException("More than 1 suitable graph builder found for languages $languages")
      }
      return suitableGraphBuilders[0]
    }
  }

  fun build(project: Project, projectRoots: List<String>): CallGraph
}



