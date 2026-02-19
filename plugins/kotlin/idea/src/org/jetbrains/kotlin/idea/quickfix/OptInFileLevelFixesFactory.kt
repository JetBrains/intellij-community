// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * [OptInFileLevelFixesFactory] is responsible for adding fixes
 * such as 'Opt in for 'API' in containing file '....kt' and low priority 'Opt in for 'API' in module '...''
 *
 * The logic for adding OptIn on code elements is in [OptInFixesFactory]
 */
internal object OptInFileLevelFixesFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val element = diagnostic.psiElement.findParentOfType<KtElement>() ?: return emptyList()

        val annotationFqName = OptInFixesUtils.annotationFqName(diagnostic) ?: return emptyList()

        val moduleDescriptor = element.getResolutionFacade().moduleDescriptor

        val annotationClassDescriptor =
            moduleDescriptor.resolveClassByFqName(annotationFqName, NoLookupLocation.FROM_IDE) ?: return emptyList()
        if (!OptInFixesUtils.annotationIsVisible(annotationClassDescriptor, from = element, element.analyze())) {
            return emptyList()
        }

        val optInFqName = OptInFixesUtils.optInFqName(moduleDescriptor)

        val containingFile = element.containingKtFile

        val result = mutableListOf<IntentionAction>()
        result += UseOptInFileAnnotationFix(
            element = containingFile,
            optInFqName = optInFqName,
            annotationFinder = { file: KtFile, annotationFqName: FqName -> findFileAnnotation(file, annotationFqName) },
            argumentClassFqName = annotationFqName,
        ).asIntention()

        containingFile.module?.let { module ->
            result += AddModuleOptInFix(
                file = containingFile,
                module = module,
                annotationFqName = annotationFqName,
            )
        }

        return result
    }

    // Find the existing file-level annotation of the specified class if it exists
    private fun findFileAnnotation(file: KtFile, annotationFqName: FqName): KtAnnotationEntry? {
        val context = file.analyze(BodyResolveMode.PARTIAL)
        return file.fileAnnotationList?.annotationEntries?.firstOrNull { entry ->
            context.get(BindingContext.ANNOTATION, entry)?.fqName == annotationFqName
        }
    }
}
