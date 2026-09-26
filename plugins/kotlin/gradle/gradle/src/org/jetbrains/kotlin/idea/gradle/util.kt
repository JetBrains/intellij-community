// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.isBuildSrcModule
import org.jetbrains.plugins.gradle.util.isIncludedBuild

/**
 * Checks whether this PSI element is located in a special Gradle source directory.
 *
 * Special Gradle source directories are those containing build configuration code rather than
 * application code:
 * - `buildSrc` directories: Gradle's conventional location for custom build logic and plugins
 * - Included build modules: modules from composite builds (configured via `includeBuild()` in settings)
 *
 * These directories may require different IDE behavior, such as filtering Gradle-generated
 * accessor completions that are intended for `.gradle.kts` files.
 *
 * @see org.jetbrains.plugins.gradle.util.isBuildSrcModule
 * @see org.jetbrains.plugins.gradle.util.isIncludedBuild
 */
fun PsiElement.isUnderSpecialSrcDirectory(): Boolean {
    if (!RootKindFilter.projectSources.matches(this)) return false
    val project = this.project
    val virtualFile = this.containingFile.virtualFile

    val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile) ?: return false
    val moduleData = GradleUtil.findGradleModuleData(module)?.data ?: return false
    return moduleData.isBuildSrcModule() || moduleData.isIncludedBuild
}