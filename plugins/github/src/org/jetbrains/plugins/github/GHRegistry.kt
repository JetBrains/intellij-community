// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import org.jetbrains.plugins.github.pullrequest.GHNewPRDiffVirtualFile
import org.jetbrains.plugins.github.pullrequest.GHPRDiffVirtualFile

object GHRegistry {
  fun isCombinedDiffEnabled(): Boolean = Registry.`is`("github.enable.combined.diff")
}

class GHRegistryValueListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (value.key == "github.enable.combined.diff") {
      for (project in ProjectManager.getInstance().openProjects) {
        DiffEditorViewerFileEditor.reloadDiffEditorsForFiles(project) { it is GHPRDiffVirtualFile || it is GHNewPRDiffVirtualFile }
      }
    }
  }
}
