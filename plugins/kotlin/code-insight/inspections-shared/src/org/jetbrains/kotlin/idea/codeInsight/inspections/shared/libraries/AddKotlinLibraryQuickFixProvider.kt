// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.libraries

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.ModulesScope
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.libraries.AddKotlinLibraryQuickFixProvider.*
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.kotlin.idea.configuration.isProjectSyncPendingOrInProgress
import org.jetbrains.kotlin.idea.configuration.withScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * A quick fix provider allowing for Kotlin libraries to be added when an unresolved element was detected.
 * [libraryGroupId] and [libraryArtifactId] are the maven coordinates that will be added by this quick-fix.
 */
open class AddKotlinLibraryQuickFixProvider(
    private val libraryGroupId: String,
    private val libraryArtifactId: String,
    private val libraryDescriptorProvider: LibraryDescriptorProvider,
    private val libraryAvailabilityTester: LibraryAvailabilityTester,
    private val libraryReferenceTester: LibraryReferenceTester,
    @IntentionName
    private val quickFixText: String = KotlinBundle.message("add.0.library", "$libraryGroupId:$libraryArtifactId")
) : UnresolvedReferenceQuickFixProvider<PsiReference>() {

    fun interface LibraryAvailabilityTester {
        /**
         * Returns whether the library that will be added by this quick-fix is already present in the module.
         */
        fun isAvailable(module: Module): Boolean

        companion object
    }

    fun interface LibraryReferenceTester {
        /**
         * Checks if the reference is a reference to a definition of the library belonging to this quick-fix.
         */
        fun isLibraryReference(ref: PsiReference): Boolean

        companion object
    }

    fun interface LibraryDescriptorProvider {
        fun getLibraryDescriptor(libraryGroupId: String, libraryArtifactId: String, psiReference: PsiReference): ExternalLibraryDescriptor?

        companion object
    }

    override fun registerFixes(ref: PsiReference, registrar: QuickFixActionRegistrar) {
        if (!libraryReferenceTester.isLibraryReference(ref)) return
        val module = ref.element.module ?: return
        val extensionList = ref.element.project.extensionArea.getExtensionPoint(KotlinBuildSystemDependencyManager.EP_NAME).extensionList
        val dependencyManager = extensionList.firstOrNull { it.isApplicable(module) } ?: return
        if (dependencyManager.isProjectSyncPendingOrInProgress()) return

        if (libraryAvailabilityTester.isAvailable(module)) return

        val libraryDescriptor = libraryDescriptorProvider.getLibraryDescriptor(
            libraryGroupId, libraryArtifactId, ref
        ) ?: return

        val scope = if (ProjectFileIndex.getInstance(module.project).isInTestSourceContent(ref.element.containingFile.virtualFile)) {
            DependencyScope.TEST
        } else {
            DependencyScope.COMPILE
        }

        registrar.register(
            AddKotlinLibraryQuickFix(
                dependencyManager = dependencyManager,
                libraryDescriptor = libraryDescriptor.withScope(scope),
                quickFixText = quickFixText
            )
        )
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}

/**
 * Will use the [KotlinLibraryVersionProvider] to look up the version
 */
fun LibraryDescriptorProvider.Companion.default(): LibraryDescriptorProvider =
    LibraryDescriptorProvider { libraryGroupId, libraryArtifactId, psiRef ->
        val module = psiRef.element.module ?: return@LibraryDescriptorProvider null
        val libraryVersion = KotlinLibraryVersionProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
            provider.getVersion(module, libraryGroupId, libraryArtifactId)
        } ?: return@LibraryDescriptorProvider null

        ExternalLibraryDescriptor(libraryGroupId, libraryArtifactId, libraryVersion, libraryVersion, libraryVersion)
    }


/**
 * A simple [LibraryReferenceTester] that determines if an unresolved reference belongs to the library if they
 * are contained in the [names].
 * The names that are checked are references or function calls that are not part of a dot qualified expression.
 */
fun LibraryReferenceTester.Companion.knownNames(vararg names: String) = LibraryReferenceTester { ref ->
    val referenceExpression = ref.element as? KtReferenceExpression ?: return@LibraryReferenceTester false
    if (referenceExpression.parent is KtQualifiedExpression) return@LibraryReferenceTester false
    referenceExpression.text in names
}

/**
 * Will check if the provided [fqn] is available as class in the given module
 */
fun LibraryAvailabilityTester.Companion.knownClassFqn(fqn: String) = LibraryAvailabilityTester { module ->
    val scope = ModulesScope.moduleWithDependenciesAndLibrariesScope(module)
    JavaPsiFacade.getInstance(module.project).findClasses(fqn, scope).isNotEmpty() ||
            KotlinFullClassNameIndex[fqn, module.project, scope].isNotEmpty()
}