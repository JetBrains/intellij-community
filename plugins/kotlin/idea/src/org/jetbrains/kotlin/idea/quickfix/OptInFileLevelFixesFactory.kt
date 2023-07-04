// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.module.Module
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * [OptInFileLevelFixesFactory] is responsible for adding fixes
 * such as 'Opt in for 'API' in containing file '....kt' and low priority 'Add '-opt-in=API' to module ... compiler arguments'
 *
 * The logic for adding OptIn on code elements is in [OptInFixesFactory]
 */
internal object OptInFileLevelFixesFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val element = diagnostic.psiElement.findParentOfType<KtElement>() ?: return emptyList()

        val annotationFqName = OptInFixesUtils.annotationFqName(diagnostic) ?: return emptyList()

        val moduleDescriptor = element.getResolutionFacade().moduleDescriptor
        val optInFqName = OptInFixesUtils.optInFqName(moduleDescriptor)

        val result = mutableListOf<IntentionAction>()

        val containingFile = element.containingKtFile
        val module = containingFile.module

        result.add(
            UseOptInFileAnnotationFix(
                containingFile, optInFqName, annotationFqName,
                findFileAnnotation(containingFile, optInFqName)?.createSmartPointer()
            )
        )

        if (module != null) {
            result.add(LowPriorityMakeModuleOptInFix(containingFile, module, annotationFqName))
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


    /**
     * A specialized version of [AddFileAnnotationFix] that adds @OptIn(...) annotations to the containing file.
     *
     * This class reuses the parent's [invoke] method, but overrides the [getText] method to provide
     * more descriptive opt-in related messages.
     *
     * @param file the file there the annotation should be added
     * @param optInFqName name of OptIn annotation
     * @param argumentClassFqName the fully qualified name of the annotation to opt-in
     * @param existingAnnotationEntry the already existing annotation entry (if any)
     */
    private open class UseOptInFileAnnotationFix(
        file: KtFile,
        optInFqName: FqName,
        private val argumentClassFqName: FqName,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>?
    ) : AddFileAnnotationFix(file, optInFqName, argumentClassFqName, existingAnnotationEntry) {
        private val fileName = file.name

        override fun getText(): String {
            val argumentText = argumentClassFqName.shortName().asString()
            return KotlinBundle.message("fix.opt_in.text.use.containing.file", argumentText, fileName)
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")
    }

    private class LowPriorityMakeModuleOptInFix(
        file: KtFile,
        module: Module,
        annotationFqName: FqName
    ) : MakeModuleOptInFix(file, module, annotationFqName), LowPriorityAction

}
