// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.projectView

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.Optional
import java.util.concurrent.ConcurrentMap

internal data class GradleDirectoryDecoration(
  val shortName: @NlsSafe String,
  val isSourceSet: Boolean,
  val appendModuleName: Boolean,
)

private val IS_GRADLE_PROJECT_KEY = Key.create<CachedValue<Boolean>>("gradle.is.gradle.project")
private val DIRECTORY_DECORATIONS_KEY =
  Key.create<CachedValue<ConcurrentMap<VirtualFile, Optional<GradleDirectoryDecoration>>>>("gradle.directory.decorations.for.project.view")

internal fun isGradleProject(project: Project): Boolean {
  return CachedValuesManager.getManager(project).getCachedValue(
    project,
    IS_GRADLE_PROJECT_KEY,
    {
      val hasLinkedProjects = GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty()
      CachedValueProvider.Result.create(hasLinkedProjects, ProjectRootManager.getInstance(project))
    },
    false,
  )
}

internal fun gradleDirectoryDecoration(virtualFile: VirtualFile, project: Project): GradleDirectoryDecoration? {
  val decorations = CachedValuesManager.getManager(project).getCachedValue(
    project,
    DIRECTORY_DECORATIONS_KEY,
    {
      val map = ConcurrentFactoryMap.createMap<VirtualFile, Optional<GradleDirectoryDecoration>> { file ->
        Optional.ofNullable(computeDecoration(file, project))
      }
      CachedValueProvider.Result.create(
        map,
        ProjectRootManager.getInstance(project),
        GradleDirectoryDecorationTracker.getInstance(project),
      )
    },
    false,
  )
  return decorations[virtualFile]?.orElse(null)
}

private fun computeDecoration(virtualFile: VirtualFile, project: Project): GradleDirectoryDecoration? {
  if (!ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) return null
  val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(virtualFile) ?: return null
  val shortName = getGradleModuleShortName(module) ?: return null
  val isSourceSet = isGradleSourceSetModule(module)
  val appendModuleName = shortName.isNotEmpty() && !shortName.replace("-", "").equals(virtualFile.name.replace("-", ""), ignoreCase = true)
  if (!appendModuleName && !isSourceSet) return null
  return GradleDirectoryDecoration(shortName, isSourceSet, appendModuleName)
}

internal fun getGradleModuleShortName(module: Module): @NlsSafe String? {
  if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return null

  if (isGradleSourceSetModule(module)) {
    return GradleProjectResolverUtil.getSourceSetName(module)
  }

  val moduleShortName = ExternalSystemApiUtil.getExternalProjectId(module)
  val isRootModule = StringUtil.equals(
    ExternalSystemApiUtil.getExternalProjectPath(module),
    ExternalSystemApiUtil.getExternalRootProjectPath(module),
  )
  return if (isRootModule || moduleShortName == null) {
    moduleShortName
  }
  else {
    val shortened = ModuleGrouper.instanceFor(module.project).getShortenedNameByFullModuleName(moduleShortName)
    StringUtil.getShortName(shortened, ':')
  }
}

internal fun isGradleSourceSetModule(module: Module): Boolean =
  GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY == ExternalSystemApiUtil.getExternalModuleType(module)

@Service(Service.Level.PROJECT)
internal class GradleDirectoryDecorationTracker : SimpleModificationTracker(), Disposable {
  init {
    Registry.get("project.qualified.module.names").addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        incModificationCount()
      }
    }, this)
  }

  override fun dispose() {}

  companion object {
    fun getInstance(project: Project): GradleDirectoryDecorationTracker = project.service()
  }
}
