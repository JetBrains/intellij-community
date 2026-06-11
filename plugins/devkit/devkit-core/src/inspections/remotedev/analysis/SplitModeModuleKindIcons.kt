// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.devkit.core.icons.DevkitCoreIcons.BackendModule
import com.intellij.devkit.core.icons.DevkitCoreIcons.FrontendModule
import com.intellij.devkit.core.icons.DevkitCoreIcons.SharedModule
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
object SplitModeModuleKindIcons {
  @JvmStatic
  fun getDescriptorIcon(descriptorFile: PsiFile): Icon? {
    val kindId = recognizeSplitModeModuleKind(descriptorFile)?.kindId ?: return null
    if (kindId == SplitModeApiRestrictionsService.ModuleKind.SHARED.id && descriptorFile.name == "plugin.xml") {
      return AllIcons.Nodes.Plugin
    }
    return getKindIcon(kindId)
  }

  @JvmStatic
  fun getKindIcon(kindId: String?): Icon? {
    return when (kindId) {
      SplitModeApiRestrictionsService.ModuleKind.SHARED.id -> SharedModule
      SplitModeApiRestrictionsService.ModuleKind.FRONTEND.id -> FrontendModule
      SplitModeApiRestrictionsService.ModuleKind.BACKEND.id -> BackendModule
      else -> null
    }
  }
}
