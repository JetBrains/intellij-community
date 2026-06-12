// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.devkit.core.icons.DevkitCoreIcons.BackendModule
import com.intellij.devkit.core.icons.DevkitCoreIcons.FrontendModule
import com.intellij.devkit.core.icons.DevkitCoreIcons.SharedModule
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
object SplitModeModuleKindIcons {
  @JvmStatic
  fun getDescriptorIcon(descriptorFile: PsiFile): Icon? {
    if (DumbService.isDumb(descriptorFile.project)) return null
    val kind = recognizeSplitModeModuleKind(descriptorFile)?.kind ?: return null
    return getKindIcon(kind, descriptorFile.name)
  }

  @JvmStatic
  fun getKindIcon(kindId: SplitModeApiRestrictionsService.ModuleKind, descriptorFileName: String): Icon? {
    @Suppress("IntroduceWhenSubject")
    return when {
      kindId == SplitModeApiRestrictionsService.ModuleKind.SHARED && descriptorFileName == "plugin.xml" -> AllIcons.Nodes.Plugin
      kindId == SplitModeApiRestrictionsService.ModuleKind.SHARED && followsSharedModuleNamingConvention(descriptorFileName) -> SharedModule
      kindId == SplitModeApiRestrictionsService.ModuleKind.SHARED -> null
      kindId == SplitModeApiRestrictionsService.ModuleKind.FRONTEND -> FrontendModule
      kindId == SplitModeApiRestrictionsService.ModuleKind.BACKEND -> BackendModule
      else -> null
    }
  }

  // heuristic to avoid using split mode specific shared module icon for any module that is technically shared
  private fun followsSharedModuleNamingConvention(moduleName: @NlsSafe String): Boolean {
    return moduleName.endsWith(".shared.xml") || moduleName.endsWith(".common.xml") || moduleName.contains(".frontback")
  }
}
