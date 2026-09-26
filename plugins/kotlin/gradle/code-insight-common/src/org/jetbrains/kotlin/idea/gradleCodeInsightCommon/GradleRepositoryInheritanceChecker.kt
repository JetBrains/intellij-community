// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

private const val ALLPROJECTS_BLOCK_NAME: String = "allprojects"
private const val SUBPROJECTS_BLOCK_NAME: String = "subprojects"

@ApiStatus.Internal
object GradleRepositoryInheritanceChecker {
    /**
     * Checks whether this project receives Maven Central or JCenter from an `allprojects` / `subprojects` block in its Gradle hierarchy.
     *
     * When the Gradle model is unavailable, the fallback checks only the nearest enclosing build's root script. It cannot account for
     * intermediate project scopes, remapped project directories, or relationships between composite builds until the project is imported.
     */
    fun hasRepositoryConfiguredInHierarchy(scriptFile: PsiFile): Boolean {
        val project = scriptFile.project
        // Configurators may operate on a non-physical PSI copy, so resolve its backing file through the original PSI file.
        val virtualFile = scriptFile.virtualFile ?: scriptFile.originalFile.virtualFile ?: return false
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
        val externalRootProjectPath = module?.let(ExternalSystemApiUtil::getExternalRootProjectPath)
        val externalProjectPath = module?.let(ExternalSystemApiUtil::getExternalProjectPath)
        if (externalRootProjectPath != null && externalProjectPath != null) {
            // Repositories may be inherited from an `allprojects` / `subprojects` block declared in the project itself
            // or in any of its ancestors (including intermediate projects), so walk the whole logical Gradle hierarchy.
            val hierarchy = collectGradleProjectHierarchy(project, externalRootProjectPath, externalProjectPath)
            if (hierarchy != null) {
                return hierarchy.any { entry ->
                    val buildScript = getBuildScriptPsiFile(project, entry.externalProjectPath)
                        ?: return@any false
                    // A project only inherits from its own `allprojects`, but from both scopes of every strict ancestor.
                    val scopeNames = if (entry.isSelf) {
                        listOf(ALLPROJECTS_BLOCK_NAME)
                    } else {
                        listOf(ALLPROJECTS_BLOCK_NAME, SUBPROJECTS_BLOCK_NAME)
                    }
                    GradleBuildScriptSupport.findManipulator(buildScript)?.hasRepositoryConfiguredInScope(scopeNames) == true
                }
            }
        }

        // Without the Gradle model we cannot reliably determine repository inheritance.
        // Physical directory layout may differ from the logical Gradle hierarchy due to
        // `projectDir` remapping, intermediate projects, or composite builds.
        //
        // The only case we can determine locally is the root project itself: if this
        // build script lives next to `settings.gradle[.kts]`, it can inherit only from
        // its own `allprojects` block (never from `subprojects`).
        val settingsDirectory = virtualFile.parent
        val isRootBuildScript = settingsDirectory.findChild("settings.gradle.kts") != null ||
                settingsDirectory.findChild("settings.gradle") != null

        if (!isRootBuildScript) return false

        return GradleBuildScriptSupport.findManipulator(scriptFile)
            ?.hasRepositoryConfiguredInScope(listOf("allprojects")) == true
    }
}
