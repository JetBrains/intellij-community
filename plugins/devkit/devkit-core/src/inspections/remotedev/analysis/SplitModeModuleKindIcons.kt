// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev.analysis

import com.intellij.devkit.core.icons.DevkitCoreIcons.BackendModule
import com.intellij.devkit.core.icons.DevkitCoreIcons.FrontendModule
import com.intellij.devkit.core.icons.DevkitCoreIcons.SharedModule
import com.intellij.icons.AllIcons
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.util.DescriptorUtil
import javax.swing.Icon

@ApiStatus.Internal
object SplitModeModuleKindIcons {
  @JvmStatic
  fun isCustomIconsEnabled(): Boolean {
    return Registry.`is`("devkit.split.mode.custom.icons", false)
  }

  @JvmStatic
  fun getDescriptorIcon(descriptorFile: PsiFile): Icon? {
    if (!isCustomIconsEnabled() || DumbService.isDumb(descriptorFile.project)) return null
    val kind = getOrComputeModuleKindFast(descriptorFile) ?: return null
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

  private fun recognizeLightweightModuleKind(descriptorFile: PsiFile): SplitModeApiRestrictionsService.ModuleKind? {
    val explicitDependencyKinds = collectExplicitDependencyKinds(descriptorFile)
    return when {
      explicitDependencyKinds.size > 1 -> SplitModeApiRestrictionsService.ModuleKind.MIXED
      explicitDependencyKinds.size == 1 -> explicitDependencyKinds.single()
      else -> inferModuleKindFromFileName(descriptorFile.name)
    }
  }

  private fun getOrComputeModuleKindFast(descriptorFile: PsiFile): SplitModeApiRestrictionsService.ModuleKind? {
    return CachedValuesManager.getCachedValue(descriptorFile) {
      CachedValueProvider.Result.create(
        recognizeLightweightModuleKind(descriptorFile),
        descriptorFile.manager.modificationTracker.forLanguage(XMLLanguage.INSTANCE),
      )
    }
  }

  private fun collectExplicitDependencyKinds(descriptorFile: PsiFile): Set<SplitModeApiRestrictionsService.ModuleKind> {
    val xmlFile = descriptorFile as? XmlFile ?: return emptySet()
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile) ?: return emptySet()
    val explicitDependencyKinds = LinkedHashSet<SplitModeApiRestrictionsService.ModuleKind>()
    for (dependencyName in SplitModeDescriptorDependencyAnalyzer.collectDirectDependencyNames(ideaPlugin)) {
      val dependencyKind = recognizeExplicitDependencyKind(dependencyName) ?: continue
      explicitDependencyKinds.add(dependencyKind)
    }
    return explicitDependencyKinds
  }

  private fun inferModuleKindFromFileName(descriptorFileName: @NlsSafe String): SplitModeApiRestrictionsService.ModuleKind? {
    return when {
      descriptorFileName == "plugin.xml" -> SplitModeApiRestrictionsService.ModuleKind.SHARED
      descriptorFileName.endsWith(".frontend.xml") -> SplitModeApiRestrictionsService.ModuleKind.FRONTEND
      descriptorFileName.endsWith(".backend.xml") -> SplitModeApiRestrictionsService.ModuleKind.BACKEND
      followsSharedModuleNamingConvention(descriptorFileName) -> SplitModeApiRestrictionsService.ModuleKind.SHARED
      else -> null
    }
  }
}
