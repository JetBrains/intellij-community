// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection.Companion.asKtClass
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.refactoring.isOpen
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * [OptInFixesFactory] is responsible for adding fixes for code elements only,
 * for example, "Opt in for 'MyExperimentalAPI' on containing class 'Bar'"
 *
 * The logic for adding OptIn to the entire file or as a compiler argument is in [OptInFileLevelFixesFactory]
 */
internal object OptInFixesFactory : KotlinIntentionActionsFactory() {
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

        val annotationFqName = OptInFixesUtils.annotationFqName(diagnostic) ?: return emptyList()
        val moduleDescriptor = containingDeclaration.resolveToDescriptorIfAny()?.module ?: return emptyList()
        val annotationClassDescriptor =
            moduleDescriptor.resolveClassByFqName(annotationFqName, NoLookupLocation.FROM_IDE) ?: return emptyList()

        val applicableTargets = AnnotationChecker.applicableTargetSet(annotationClassDescriptor)
        val context = when (element) {
            is KtElement -> element.analyze()
            else -> containingDeclaration.analyze()
        }

        val isOverrideError = diagnostic.factory == OPT_IN_OVERRIDE_ERROR || diagnostic.factory == OPT_IN_OVERRIDE
        val optInFqName = OptInFixesUtils.optInFqName(moduleDescriptor)

        val result = mutableListOf<IntentionAction>()

        fun collectPropagateOptInAnnotationFix(targetElement: KtDeclaration, kind: AddAnnotationFix.Kind) {
            val elementDescriptor = targetElement.toDescriptor() as? ClassDescriptor
            val actualTargetList = AnnotationChecker.getDeclarationSiteActualTargetList(targetElement, elementDescriptor, context)
            if (actualTargetList.none { it in applicableTargets }) return

            val quickFix = when {
                // When we are fixing a missing annotation on an overridden function, we should
                // propose to add a propagating annotation first, and in all other cases
                // the non-propagating opt-in annotation should be default.
                // The same logic applies to the similar conditional expressions onward.
                isOverrideError -> HighPriorityPropagateOptInAnnotationFix(targetElement, annotationFqName, kind)

                targetElement.isSubclassOptPropagateApplicable(annotationFqName) -> PropagateOptInAnnotationFix(
                    targetElement, OptInNames.SUBCLASS_OPT_IN_REQUIRED_FQ_NAME, kind, annotationFqName
                )

                else -> PropagateOptInAnnotationFix(targetElement, annotationFqName, kind)
            }
            result.add(quickFix)
        }

        fun collectUseOptInAnnotationFix(targetElement: KtDeclaration, kind: AddAnnotationFix.Kind) {
            val existingAnnotationEntry = targetElement.findAnnotation(optInFqName)?.createSmartPointer()

            val quickFix =
                if (isOverrideError) UseOptInAnnotationFix(
                    targetElement,
                    optInFqName,
                    kind,
                    annotationFqName,
                    existingAnnotationEntry
                )
                else HighPriorityUseOptInAnnotationFix(targetElement, optInFqName, kind, annotationFqName, existingAnnotationEntry)

            result.add(quickFix)
        }

        val kind = if (containingDeclaration is KtConstructor<*>) AddAnnotationFix.Kind.Constructor
        else AddAnnotationFix.Kind.Declaration(containingDeclaration.name)

        collectPropagateOptInAnnotationFix(containingDeclaration, kind)
        collectUseOptInAnnotationFix(containingDeclaration, kind)

        val containingClassOrObject = containingDeclaration.containingClassOrObject
        if (containingDeclaration is KtCallableDeclaration && containingClassOrObject != null) {
            val kind1 = AddAnnotationFix.Kind.ContainingClass(containingClassOrObject.name)
            collectPropagateOptInAnnotationFix(containingClassOrObject, kind1)
            collectUseOptInAnnotationFix(containingClassOrObject, kind1)
        }

        return result
    }

    private fun KtDeclaration.isSubclassOptPropagateApplicable(annotationFqName: FqName): Boolean {
        if (this !is KtClass) return false

        // SubclassOptInRequired is inapplicable on sealed classes and interfaces, final classes,
        // open local classes, object, enum classes and fun interfaces
        check(!this.isLocal) { "Local declarations are filtered in OptInFixesFactory.doCreateActions" }
        if (this.isSealed() || this.hasModifier(KtTokens.FUN_KEYWORD) || !this.isOpen()) return false
        if (this.descriptor?.annotations?.findAnnotation(OptInNames.SUBCLASS_OPT_IN_REQUIRED_FQ_NAME) != null) return false
        return superTypeListEntries.any {
            val superClassDescriptor = it.asKtClass()?.descriptor ?: return@any false
            val superClassAnnotation =
                superClassDescriptor.annotations.findAnnotation(OptInNames.SUBCLASS_OPT_IN_REQUIRED_FQ_NAME) ?: return@any false
            val apiFqName = superClassAnnotation.allValueArguments[OptInNames.OPT_IN_ANNOTATION_CLASS]?.safeAs<KClassValue>()
                ?.getArgumentType(superClassDescriptor.module)?.fqName
            apiFqName == annotationFqName
        }
    }


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
    ) : UseOptInAnnotationFix(element, optInFqName, kind, argumentClassFqName, existingAnnotationEntry), HighPriorityAction


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
     * @param argumentClassFqName the qualified class name to be added to the annotation entry in the format '::class'
     * @param existingAnnotationEntry the already existing annotation entry (if any)
     *
     */
    private open class PropagateOptInAnnotationFix(
        element: KtDeclaration,
        private val annotationFqName: FqName,
        private val kind: Kind,
        private val argumentClassFqName: FqName? = null,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : AddAnnotationFix(element, annotationFqName, Kind.Self, argumentClassFqName, existingAnnotationEntry) {

        override fun getText(): String {
            val annotationName = annotationFqName.shortName().asString()
            val annotationEntry = if (argumentClassFqName != null) "(${argumentClassFqName.shortName().asString()}::class)" else ""
            val argumentText = annotationName + annotationEntry
            return when (kind) {
                Kind.Self -> KotlinBundle.message("fix.opt_in.text.propagate.declaration", argumentText, "?")
                Kind.Constructor -> KotlinBundle.message("fix.opt_in.text.propagate.constructor", argumentText)
                is Kind.Declaration -> KotlinBundle.message("fix.opt_in.text.propagate.declaration", argumentText, kind.name ?: "?")
                is Kind.ContainingClass -> KotlinBundle.message(
                    "fix.opt_in.text.propagate.containing.class", argumentText, kind.name ?: "?"
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
    ) : PropagateOptInAnnotationFix(element, annotationFqName, kind, null, existingAnnotationEntry), HighPriorityAction

}
