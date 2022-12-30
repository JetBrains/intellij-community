// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.module.Module
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.OLD_USE_EXPERIMENTAL_FQ_NAME
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.util.findAnnotation
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

object OptInFixesFactory : KotlinIntentionActionsFactory() {
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
            OPT_IN_USAGE -> OPT_IN_USAGE.cast(diagnostic).a
            OPT_IN_USAGE_ERROR -> OPT_IN_USAGE_ERROR.cast(diagnostic).a
            OPT_IN_OVERRIDE -> OPT_IN_OVERRIDE.cast(diagnostic).a
            OPT_IN_OVERRIDE_ERROR -> OPT_IN_OVERRIDE_ERROR.cast(diagnostic).a
            else -> null
        } ?: return emptyList()
        val moduleDescriptor = containingDeclaration.resolveToDescriptorIfAny()?.module ?: return emptyList()
        val annotationClassDescriptor = moduleDescriptor.resolveClassByFqName(
            annotationFqName, NoLookupLocation.FROM_IDE
        ) ?: return emptyList()

        val applicableTargets = AnnotationChecker.applicableTargetSet(annotationClassDescriptor)
        val context = when (element) {
            is KtElement -> element.analyze()
            else -> containingDeclaration.analyze()
        }

        fun isApplicableTo(declaration: KtDeclaration): Boolean {
            val actualTargetList = AnnotationChecker.getDeclarationSiteActualTargetList(
                declaration, declaration.toDescriptor() as? ClassDescriptor, context
            )
            return actualTargetList.any { it in applicableTargets }
        }

        val isOverrideError = diagnostic.factory == OPT_IN_OVERRIDE_ERROR || diagnostic.factory == OPT_IN_OVERRIDE
        val optInFqName = OptInNames.OPT_IN_FQ_NAME.takeIf { moduleDescriptor.annotationExists(it) }
            ?: FqNames.OptInFqNames.OLD_USE_EXPERIMENTAL_FQ_NAME

        val result = mutableListOf<IntentionAction>()

        // just to avoid local variable name shadowing
        run {
            val kind = if (containingDeclaration is KtConstructor<*>)
                AddAnnotationFix.Kind.Constructor
            else
                AddAnnotationFix.Kind.Declaration(containingDeclaration.name)

            if (isApplicableTo(containingDeclaration)) {
                // When we are fixing a missing annotation on an overridden function, we should
                // propose to add a propagating annotation first, and in all other cases
                // the non-propagating opt-in annotation should be default.
                // The same logic applies to the similar conditional expressions onward.
                result.add(
                    if (isOverrideError)
                        HighPriorityPropagateOptInAnnotationFix(containingDeclaration, annotationFqName, kind)
                    else
                        PropagateOptInAnnotationFix(containingDeclaration, annotationFqName, kind)
                )
            }
            val existingAnnotationEntry = containingDeclaration.findAnnotation(optInFqName)?.createSmartPointer()
            result.add(
                if (isOverrideError)
                    UseOptInAnnotationFix(containingDeclaration, optInFqName, kind, annotationFqName, existingAnnotationEntry)
                else
                    HighPriorityUseOptInAnnotationFix(containingDeclaration, optInFqName, kind, annotationFqName, existingAnnotationEntry)
            )
        }

        if (containingDeclaration is KtCallableDeclaration) {
            val containingClassOrObject = containingDeclaration.containingClassOrObject
            if (containingClassOrObject != null) {
                val kind = AddAnnotationFix.Kind.ContainingClass(containingClassOrObject.name)
                val isApplicableToContainingClassOrObject = isApplicableTo(containingClassOrObject)
                if (isApplicableToContainingClassOrObject) {
                    result.add(
                        if (isOverrideError)
                            HighPriorityPropagateOptInAnnotationFix(containingClassOrObject, annotationFqName, kind)
                        else
                            PropagateOptInAnnotationFix(containingClassOrObject, annotationFqName, kind)
                    )
                }

                val existingAnnotationEntry = containingClassOrObject.findAnnotation(optInFqName)?.createSmartPointer()
                result.add(
                    if (isOverrideError)
                        UseOptInAnnotationFix(containingClassOrObject, optInFqName, kind, annotationFqName, existingAnnotationEntry)
                    else
                        HighPriorityUseOptInAnnotationFix(
                            containingClassOrObject, optInFqName, kind, annotationFqName, existingAnnotationEntry
                        )
                )
            }
        }

        val containingFile = containingDeclaration.containingKtFile
        val module = containingFile.module
        if (module != null) {
            result.add(LowPriorityMakeModuleOptInFix(containingFile, module, annotationFqName))
        }

        // Add the file-level annotation `@file:OptIn(...)`
        result.add(
            UseOptInFileAnnotationFix(
                containingFile, optInFqName, annotationFqName,
                findFileAnnotation(containingFile, optInFqName)?.createSmartPointer()
            )
        )
        return result
    }

    // Find the existing file-level annotation of the specified class if it exists
    private fun findFileAnnotation(file: KtFile, annotationFqName: FqName): KtAnnotationEntry? {
        val context = file.analyze(BodyResolveMode.PARTIAL)
        return file.fileAnnotationList?.annotationEntries?.firstOrNull { entry ->
            context.get(BindingContext.ANNOTATION, entry)?.fqName == annotationFqName
        }
    }

    fun ModuleDescriptor.annotationExists(fqName: FqName): Boolean =
        resolveClassByFqName(fqName, NoLookupLocation.FROM_IDE) != null

    /**
     * A specialized subclass of [AddAnnotationFix] that adds @OptIn(...) annotations to declarations,
     * containing classes, or constructors.
     *
     * This class reuses the parent's [invoke] method but overrides the [getText] method to provide
     * more descriptive opt-in-specific messages.
     *
     * @param element a declaration to annotate
     * @param optInFqName name of OptIn annotation
     * @param kind the annotation kind (desired scope)
     * @param argumentClassFqName the fully qualified name of the annotation to opt-in
     * @param existingAnnotationEntry the already existing annotation entry (if any)
     *
     */
    private open class UseOptInAnnotationFix(
        element: KtDeclaration,
        optInFqName: FqName,
        private val kind: Kind,
        private val argumentClassFqName: FqName,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : AddAnnotationFix(element, optInFqName, kind, argumentClassFqName, existingAnnotationEntry) {

        private val elementName = element.name ?: "?"

        override fun getText(): String {
            val argumentText = argumentClassFqName.shortName().asString()
            return when (kind) {
                Kind.Self -> KotlinBundle.message("fix.opt_in.text.use.declaration", argumentText, elementName)
                Kind.Constructor -> KotlinBundle.message("fix.opt_in.text.use.constructor", argumentText)
                is Kind.Declaration -> KotlinBundle.message("fix.opt_in.text.use.declaration", argumentText, kind.name ?: "?")
                is Kind.ContainingClass -> KotlinBundle.message("fix.opt_in.text.use.containing.class", argumentText, kind.name ?: "?")
            }
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")
    }

    private class HighPriorityUseOptInAnnotationFix(
        element: KtDeclaration,
        optInFqName: FqName,
        kind: Kind,
        argumentClassFqName: FqName,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : UseOptInAnnotationFix(element, optInFqName, kind, argumentClassFqName, existingAnnotationEntry),
        HighPriorityAction

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

    /**
     * A specialized subclass of [AddAnnotationFix] that adds propagating opted-in annotations
     * to declarations, containing classes, or constructors.
     *
     * This class reuses the parent's [invoke] method but overrides the [getText] method to provide
     * more descriptive opt-in-specific messages.
     *
     * @param element a declaration to annotate
     * @param annotationFqName the fully qualified name of the annotation
     * @param kind the annotation kind (desired scope)
     * @param existingAnnotationEntry the already existing annotation entry (if any)
     *
     */
    private open class PropagateOptInAnnotationFix(
        element: KtDeclaration,
        private val annotationFqName: FqName,
        private val kind: Kind,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : AddAnnotationFix(element, annotationFqName, Kind.Self, null, existingAnnotationEntry) {
        override fun getText(): String {
            val argumentText = annotationFqName.shortName().asString()
            return when (kind) {
                Kind.Self -> KotlinBundle.message("fix.opt_in.text.propagate.declaration", argumentText, "?")
                Kind.Constructor -> KotlinBundle.message("fix.opt_in.text.propagate.constructor", argumentText)
                is Kind.Declaration -> KotlinBundle.message("fix.opt_in.text.propagate.declaration", argumentText, kind.name ?: "?")
                is Kind.ContainingClass -> KotlinBundle.message(
                    "fix.opt_in.text.propagate.containing.class",
                    argumentText,
                    kind.name ?: "?"
                )
            }
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")
    }

    /**
     * A high-priority version of [PropagateOptInAnnotationFix] (for overridden constructor case)
     *
     * @param element a declaration to annotate
     * @param annotationFqName the fully qualified name of the annotation
     * @param kind the annotation kind (desired scope)
     * @param existingAnnotationEntry the already existing annotation entry (if any)
     */
    private class HighPriorityPropagateOptInAnnotationFix(
        element: KtDeclaration,
        annotationFqName: FqName,
        kind: Kind,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : PropagateOptInAnnotationFix(element, annotationFqName, kind, existingAnnotationEntry),
        HighPriorityAction

    private class LowPriorityMakeModuleOptInFix(
        file: KtFile,
        module: Module,
        annotationFqName: FqName
    ) : MakeModuleOptInFix(file, module, annotationFqName), LowPriorityAction
}
