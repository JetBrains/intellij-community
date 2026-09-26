// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.scaffolding

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
data class CreatedIjModuleInfo(
  val moduleName: String,
  val moduleRootPath: String,
  val kindTemplateName: String,
  val targetPluginXmlPath: String?,
)

@ApiStatus.Internal
suspend fun createIjModuleWithoutUi(
  project: Project,
  newModuleParentDirectory: VirtualFile,
  moduleName: String,
  kindTemplateName: String,
): CreatedIjModuleInfo {
  val popupContext = collectNewIjModuleCreationContext(newModuleParentDirectory, project)
  val kind = parseIjModuleKind(kindTemplateName)
  val createdModule = createIjModule(
    project,
    newModuleParentDirectory,
    moduleName,
    kind,
    popupContext.targetPlugin,
  )
  return CreatedIjModuleInfo(
    moduleName = createdModule.moduleName,
    moduleRootPath = project.toProjectRelativePath(createdModule.moduleRoot),
    kindTemplateName = createdModule.moduleKind.templateName,
    targetPluginXmlPath = popupContext.targetPlugin?.pluginXml?.virtualFile?.toNioPath()?.let(project::toProjectRelativePath),
  )
}

private fun parseIjModuleKind(kindTemplateName: String): IjModuleKind {
  val normalizedKindTemplateName = kindTemplateName.trim().lowercase()
  return IjModuleKind.entries.firstOrNull { it.templateName == normalizedKindTemplateName }
         ?: error("Unknown IntelliJ module kind '$kindTemplateName'. Expected one of: ${IjModuleKind.entries.joinToString { it.templateName }}")
}

private fun Project.toProjectRelativePath(path: Path): String {
  val projectBasePath = basePath ?: return path.toAbsolutePath().normalize().invariantSeparatorsPathString
  val normalizedPath = path.toAbsolutePath().normalize()
  return runCatching {
    Path.of(projectBasePath).toAbsolutePath().normalize().relativize(normalizedPath).invariantSeparatorsPathString
  }.getOrElse {
    normalizedPath.invariantSeparatorsPathString
  }
}
