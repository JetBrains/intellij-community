package com.intellij.cce.callGraphs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface CallGraphBuilder {

  companion object {
    val EP_NAME: ExtensionPointName<CallGraphBuilder> = ExtensionPointName.create("com.intellij.cce.callGraphBuilder")
  }

  fun build(project: Project): CallGraph
}

