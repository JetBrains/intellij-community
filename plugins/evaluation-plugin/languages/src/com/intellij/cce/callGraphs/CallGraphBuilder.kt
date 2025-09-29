package com.intellij.cce.callGraphs

import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface CallGraphBuilder {
  val language: Language

  companion object {
    val EP_NAME: ExtensionPointName<CallGraphBuilder> = ExtensionPointName.create("com.intellij.cce.callGraphBuilder")

    fun getForLanguage(language: Language): CallGraphBuilder {
      val suitableGraphBuilders = EP_NAME.extensionList.filter { it.language == language }
      if (suitableGraphBuilders.isEmpty()) {
        throw IllegalStateException("No suitable graph builder found for language $language")
      }
      if (suitableGraphBuilders.size > 1) {
        throw IllegalStateException("More than 1 suitable graph builder found for language $language")
      }
      return suitableGraphBuilders[0]
    }
  }

  fun build(project: Project): CallGraph
}

