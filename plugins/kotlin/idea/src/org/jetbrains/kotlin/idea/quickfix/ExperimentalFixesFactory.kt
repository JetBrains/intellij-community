// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.module.Module
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

object ExperimentalFixesFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val element = diagnostic.psiElement
        val containingDeclaration: KtDeclaration = element.getParentOfTypesAndPredicate(
            true,
            KtDeclarationWithBody::class.java,
            KtClassOrObject::class.java,
            KtProperty::class.java,
            KtTypeAlias::class.java
        ) {
            !KtPsiUtil.isLocal(it)
        } ?: return emptyList()

        val annotationFqName = when (diagnostic.factory) {
            EXPERIMENTAL_API_USAGE -> EXPERIMENTAL_API_USAGE.cast(diagnostic).a
            EXPERIMENTAL_API_USAGE_ERROR -> EXPERIMENTAL_API_USAGE_ERROR.cast(diagnostic).a
            EXPERIMENTAL_OVERRIDE -> EXPERIMENTAL_OVERRIDE.cast(diagnostic).a
            EXPERIMENTAL_OVERRIDE_ERROR -> EXPERIMENTAL_OVERRIDE_ERROR.cast(diagnostic).a
            else -> null
        } ?: return emptyList()

        val moduleDescriptor = containingDeclaration.resolveToDescriptorIfAny()?.module ?: return emptyList()
        val annotationClassDescriptor = moduleDescriptor.resolveClassByFqName(
            annotationFqName, NoLookupLocation.FROM_IDE
        ) ?: return emptyList()
        val applicableTargets = AnnotationChecker.applicableTargetSet(annotationClassDescriptor) ?: KotlinTarget.DEFAULT_TARGET_SET

        val context = when (element) {
            is KtElement -> element.analyze()
            else -> containingDeclaration.analyze()
        }

        fun isApplicableTo(declaration: KtDeclaration, applicableTargets: Set<KotlinTarget>): Boolean {
            val actualTargetList = AnnotationChecker.getDeclarationSiteActualTargetList(
                declaration, declaration.toDescriptor() as? ClassDescriptor, context
            )
            return actualTargetList.any { it in applicableTargets }
        }

        val result = mutableListOf<IntentionAction>()
        run {
            val kind = AddAnnotationFix.Kind.Declaration(containingDeclaration.name)
            if (isApplicableTo(containingDeclaration, applicableTargets)) {
                result.add(AddAnnotationFix(containingDeclaration, annotationFqName, kind))
            }
            result.add(
                HighPriorityAddAnnotationFix(
                    containingDeclaration, moduleDescriptor.OPT_IN_FQ_NAME, kind, annotationFqName,
                    containingDeclaration.findAnnotation(moduleDescriptor.OPT_IN_FQ_NAME)?.createSmartPointer()
                )
            )
        }
        if (containingDeclaration is KtCallableDeclaration) {
            val containingClassOrObject = containingDeclaration.containingClassOrObject
            if (containingClassOrObject != null) {
                val kind = AddAnnotationFix.Kind.ContainingClass(containingClassOrObject.name)
                if (isApplicableTo(containingClassOrObject, applicableTargets)) {
                    result.add(AddAnnotationFix(containingClassOrObject, annotationFqName, kind))
                } else {
                    result.add(
                        HighPriorityAddAnnotationFix(
                            containingClassOrObject, moduleDescriptor.OPT_IN_FQ_NAME, kind, annotationFqName,
                            containingDeclaration.findAnnotation(moduleDescriptor.OPT_IN_FQ_NAME)?.createSmartPointer()
                        )
                    )
                }
            }
        }
        val containingFile = containingDeclaration.containingKtFile
        val module = containingFile.module
        if (module != null) {
            result.add(
                LowPriorityMakeModuleExperimentalFix(containingFile, module, annotationFqName)
            )
        }

        // Add the file-level annotation `@file:OptIn(...)` fix
        result.add(
            AddFileAnnotationFix(
                containingFile,
                moduleDescriptor.OPT_IN_FQ_NAME,
                annotationFqName,
                findFileAnnotation(containingFile, moduleDescriptor.OPT_IN_FQ_NAME)?.createSmartPointer()
            )
        )
        return result
    }

    private val ModuleDescriptor.OPT_IN_FQ_NAME: FqName
        get() = OptInNames.OPT_IN_FQ_NAME.takeIf { fqNameIsExisting(it) }
            ?: OptInNames.OLD_USE_EXPERIMENTAL_FQ_NAME

    // Find the existing file-level annotation of the specified class if it exists
    private fun findFileAnnotation(file: KtFile, annotationFqName: FqName): KtAnnotationEntry? {
        val context = file.analyze(BodyResolveMode.PARTIAL)
        return file.fileAnnotationList?.annotationEntries?.firstOrNull { entry ->
            context.get(BindingContext.ANNOTATION, entry)?.fqName == annotationFqName
        }
    }

    fun ModuleDescriptor.fqNameIsExisting(fqName: FqName): Boolean = resolveClassByFqName(fqName, NoLookupLocation.FROM_IDE) != null

    private class HighPriorityAddAnnotationFix(
            element: KtDeclaration,
            annotationFqName: FqName,
            kind: Kind = Kind.Self,
            argumentClassFqName: FqName? = null,
            existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : AddAnnotationFix(element, annotationFqName, kind, argumentClassFqName, existingAnnotationEntry), HighPriorityAction

    private class LowPriorityMakeModuleExperimentalFix(
            file: KtFile,
            module: Module,
            annotationFqName: FqName
    ) : MakeModuleExperimentalFix(file, module, annotationFqName), LowPriorityAction
}
