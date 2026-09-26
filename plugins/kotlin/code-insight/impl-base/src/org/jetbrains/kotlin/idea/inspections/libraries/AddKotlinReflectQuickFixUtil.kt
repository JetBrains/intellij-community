// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.libraries

import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.isProjectSyncPendingOrInProgress
import org.jetbrains.kotlin.idea.configuration.withScope
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion

private const val GROUP_ID = "org.jetbrains.kotlin"
private const val ARTIFACT_ID = "kotlin-reflect"

@ApiStatus.Internal
object AddKotlinReflectQuickFixUtil {

    fun createQuickFix(element: PsiElement): ModCommandAction? {
        val module = element.module ?: return null

        val dependencyManager = KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)
            ?: return null
        if (dependencyManager.isProjectSyncPendingOrInProgress()) return null

        val version = (getRuntimeLibraryVersion(module) ?: KotlinPluginLayout.standaloneCompilerVersion).artifactVersion

        val scope = when {
            element.containingFile.virtualFile != null && ProjectFileIndex.getInstance(module.project)
                .isInTestSourceContent(element.containingFile.virtualFile) -> DependencyScope.TEST

            else -> DependencyScope.COMPILE
        }

        val libraryDescriptor = ExternalLibraryDescriptor(
            GROUP_ID,
            ARTIFACT_ID,
            version,
            version,
            version,
        ).withScope(scope)

        return AddKotlinLibraryQuickFix(
            dependencyManager = dependencyManager,
            libraryDescriptor = libraryDescriptor,
            quickFixText = KotlinBundle.message("add.kotlin.reflect.library"),
        )
    }
}
