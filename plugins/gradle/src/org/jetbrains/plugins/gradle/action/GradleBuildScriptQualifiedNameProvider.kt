// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull
import org.jetbrains.plugins.gradle.util.gradlePathOrNull
import java.nio.file.Path
import java.util.ArrayDeque
import kotlin.io.path.pathString

/**
 * Feeds **Copy Reference** via [com.intellij.ide.actions.FqnUtil]: Gradle identity path for the project that owns
 * this script, following Gradle's tooling model ([ExternalProject.getBuildFile], [ExternalProject.getProjectDir],
 * [ExternalProject.getIdentityPath]).
 */
class GradleBuildScriptQualifiedNameProvider : QualifiedNameProvider {
  override fun adjustElementToCopy(element: PsiElement): PsiElement? = null

  override fun getQualifiedName(element: PsiElement): String? {
    if (element !is PsiFile) return null
    val virtualFile = element.virtualFile ?: return null
    return findGradleProjectReferencePath(element.project, virtualFile)
  }

  override fun qualifiedNameToElement(fqn: String, project: Project): PsiElement? = null

  override fun insertQualifiedName(fqn: String, element: PsiElement, editor: Editor, project: Project) {
  }

  companion object {
    /**
     * Formats Gradle [ExternalProject.getIdentityPath] for display (task / reference style), matching
     * tooling usage in the Gradle plugin.
     */
    internal fun formatGradleIdentityForReference(identityPath: String): String =
      if (identityPath == ":") ":" else identityPath.removeSuffix(":")

    /**
     * Gradle project path as used in Gradle APIs and task paths (for example `:app:core`), from synced [ModuleData].
     * Prefer [findGradleProjectReferencePath] which uses Gradle's [ExternalProject] model when available.
     */
    internal fun gradleProjectReferencePath(moduleData: ModuleData): String? {
      val identity = moduleData.gradleIdentityPathOrNull
      if (!identity.isNullOrEmpty()) {
        return formatGradleIdentityForReference(identity)
      }
      val gradlePath = moduleData.gradlePathOrNull
      if (!gradlePath.isNullOrEmpty()) return gradlePath
      return null
    }

    /**
     * Resolves the Gradle reference path for a build or settings script using the Gradle tooling model when possible
     * (correct for composite / included builds, buildSrc, custom [org.gradle.api.initialization.Settings.getRootProject].buildFileName,
     * and non-default project directories). Falls back to [ExternalSystemModuleDataIndex] when the model is unavailable.
     */
    internal fun findGradleProjectReferencePath(project: Project, scriptFile: VirtualFile): String? {
      if (!looksLikeGradleBuildOrSettingsScript(scriptFile.name)) return null

      findOwningExternalProject(project, scriptFile)?.let { ext ->
        return formatGradleIdentityForReference(ext.identityPath)
      }

      val moduleDir = scriptFile.parent ?: return null
      val linkedPath = ExternalSystemApiUtil.toCanonicalPath(moduleDir.path)
      val moduleNode = ExternalSystemModuleDataIndex.findModuleNode(project, linkedPath) ?: return null
      val moduleData = moduleNode.data
      if (moduleData.owner != GradleConstants.SYSTEM_ID) return null
      return gradleProjectReferencePath(moduleData)
    }

    private fun looksLikeGradleBuildOrSettingsScript(fileName: String): Boolean {
      if (GradleConstants.KNOWN_GRADLE_FILES.contains(fileName)) return true
      if (fileName.endsWith(".${GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION}")) return true
      if (fileName.endsWith(".${GradleConstants.DECLARATIVE_EXTENSION}")) return true
      if (fileName.endsWith(".${GradleConstants.EXTENSION}") && !fileName.endsWith(".${GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION}")) return true
      return false
    }

    private fun canonicalIoFile(file: Path): String = ExternalSystemApiUtil.toCanonicalPath(file.pathString)

    private fun canonicalVirtualFile(vf: VirtualFile): String = ExternalSystemApiUtil.toCanonicalPath(vf.path)

    private fun findOwningExternalProject(project: Project, scriptVf: VirtualFile): ExternalProject? {
      val scriptPath = canonicalVirtualFile(scriptVf)
      val cache = ExternalProjectDataCache.getInstance(project)
      for (link in GradleSettings.getInstance(project).linkedProjectsSettings) {
        val rootPath = link.externalProjectPath?.let { ExternalSystemApiUtil.toCanonicalPath(it) } ?: continue
        val root = cache.getRootExternalProject(rootPath) ?: continue
        findOwningExternalProjectInTree(root, scriptPath)?.let { return it }
      }
      return null
    }

    private fun findOwningExternalProjectInTree(root: ExternalProject, scriptPath: String): ExternalProject? {
      val deque = ArrayDeque<ExternalProject>()
      deque.add(root)
      while (deque.isNotEmpty()) {
        val p = deque.removeFirst()
        if (externalProjectUsesScriptFile(p, scriptPath)) return p
        deque.addAll(p.childProjects.values)
      }
      return null
    }

    @Suppress("IO_FILE_USAGE")
    private fun externalProjectUsesScriptFile(p: ExternalProject, scriptPath: String): Boolean {
      val buildFile = p.buildFile?.toPath()
      if (buildFile != null && scriptPath == canonicalIoFile(buildFile)) return true
      val dir = p.projectDir.toPath()
      for (settingsName in GradleConstants.KNOWN_GRADLE_SETTINGS_FILES) {
        if (scriptPath == canonicalIoFile(dir.resolve(settingsName))) return true
      }
      return false
    }
  }
}