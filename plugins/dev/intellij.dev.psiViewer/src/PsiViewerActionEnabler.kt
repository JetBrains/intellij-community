package com.intellij.dev.psiViewer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface PsiViewerActionEnabler {
  companion object {
    private val EP_NAME: ExtensionPointName<PsiViewerActionEnabler> = ExtensionPointName("com.intellij.dev.psiViewer.psiViewerActionEnabler")

    @JvmStatic
    fun isActionEnabled(project: Project): Boolean {
      return EP_NAME.extensionList.any { it.isEnabled(project) }
    }
  }

  fun isEnabled(project: Project): Boolean
}