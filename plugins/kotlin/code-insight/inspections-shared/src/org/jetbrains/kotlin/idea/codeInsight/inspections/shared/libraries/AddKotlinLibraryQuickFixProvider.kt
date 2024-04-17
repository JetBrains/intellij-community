// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.libraries

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.config.toKotlinVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.kotlin.idea.configuration.isProjectSyncPendingOrInProgress

/**
 * A quick fix provider allowing for Kotlin libraries to be added when an unresolved element was detected.
 * [libraryGroupId] and [libraryArtifactId] are the maven coordinates that will be added by this quick-fix.
 */
abstract class AddKotlinLibraryQuickFixProvider(
    private val libraryGroupId: String,
    private val libraryArtifactId: String
) : UnresolvedReferenceQuickFixProvider<PsiReference>() {
    /**
     * Returns whether the library that will be added by this quick-fix is already present in the module.
     */
    protected abstract fun hasLibrary(module: Module): Boolean

    /**
     * Checks if the reference is a reference to a definition of the library belonging to this quick-fix.
     */
    protected abstract fun isLibraryReference(ref: PsiReference): Boolean

    private fun PsiReference.getKotlinVersion(): KotlinVersion {
        return element.languageVersionSettings.languageVersion.toKotlinVersion()
    }

    private fun getLibraryDescriptor(kotlinVersion: KotlinVersion): ExternalLibraryDescriptor? {
        val versionProvider = KotlinLibraryVersionProvider.EP_NAME.extensionList.firstOrNull() ?: return null
        return versionProvider.getVersion(libraryGroupId, libraryArtifactId, kotlinVersion)
    }

    override fun registerFixes(ref: PsiReference, registrar: QuickFixActionRegistrar) {
        if (!isLibraryReference(ref)) return
        val module = ref.element.module ?: return

        val extensionList = ref.element.project.extensionArea.getExtensionPoint(KotlinBuildSystemDependencyManager.EP_NAME).extensionList
        val dependencyManager = extensionList.firstOrNull { it.isApplicable(module) } ?: return
        if (dependencyManager.isProjectSyncPendingOrInProgress()) return

        if (hasLibrary(module)) return
        val libraryVersionToUse = getLibraryDescriptor(ref.getKotlinVersion()) ?: return

        val scope = if (ProjectFileIndex.getInstance(module.project).isInTestSourceContent(ref.element.containingFile.virtualFile)) {
            DependencyScope.TEST
        } else {
            DependencyScope.COMPILE
        }

        val scopedLibraryDescriptor = ExternalLibraryDescriptor(
            libraryVersionToUse.libraryGroupId,
            libraryVersionToUse.libraryArtifactId,
            libraryVersionToUse.minVersion,
            libraryVersionToUse.maxVersion,
            libraryVersionToUse.preferredVersion,
            scope
        )

        registrar.register(
            AddKotlinLibraryQuickFix(
                dependencyManager = dependencyManager,
                libraryDescriptor = scopedLibraryDescriptor,
                quickFixText = KotlinBundle.message("add.kotlin.coroutines")
            )
        )
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}