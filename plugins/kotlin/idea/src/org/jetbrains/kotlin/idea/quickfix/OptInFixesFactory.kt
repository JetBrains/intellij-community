// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection.Companion.asKtClass
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.refactoring.isOpen
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.sam.SamConstructorDescriptor
import org.jetbrains.kotlin.scripting.definitions.isScript
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * [OptInFixesFactory] is responsible for adding fixes for code elements only,
 * for example, "Opt in for 'MyExperimentalAPI' on containing class 'Bar'"
 *
 * The logic for adding OptIn to the entire file or as a compiler argument is in [OptInFileLevelFixesFactory]
 */
internal object OptInFixesFactory : KotlinIntentionActionsFactory() {
    private data class CandidateData(val element: KtElement, val kind: AddAnnotationFix.Kind)

    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val element = diagnostic.psiElement.findParentOfType<KtElement>(strict = false) ?: return emptyList()
        val annotationFqName = OptInFixesUtils.annotationFqName(diagnostic) ?: return emptyList()
        val moduleDescriptor = element.getResolutionFacade().moduleDescriptor
        val annotationClassDescriptor =
            moduleDescriptor.resolveClassByFqName(annotationFqName, NoLookupLocation.FROM_IDE) ?: return emptyList()

        val annotationClassId = annotationClassDescriptor.classId ?: return emptyList()

        val applicableTargets = AnnotationChecker.applicableTargetSet(annotationClassDescriptor)
        val context = element.analyze()
        val isOverrideError = diagnostic.factory == OPT_IN_OVERRIDE_ERROR || diagnostic.factory == OPT_IN_OVERRIDE
        val optInClassId = ClassId.topLevel(OptInFixesUtils.optInFqName(moduleDescriptor))

        val result = mutableListOf<IntentionAction>()

        fun collectPropagateOptInAnnotationFix(targetElement: KtElement, kind: AddAnnotationFix.Kind) {
            if (targetElement !is KtDeclaration || KtPsiUtil.isLocal(targetElement)) return
            val elementDescriptor = targetElement.toDescriptor() as? ClassDescriptor
            val actualTargetList = AnnotationChecker.getDeclarationSiteActualTargetList(targetElement, elementDescriptor, context)
            if (actualTargetList.none { it in applicableTargets }) return

            val quickFix = when {
                // When we are fixing a missing annotation on an overridden function, we should
                // propose to add a propagating annotation first, and in all other cases
                // the non-propagating opt-in annotation should be default.
                // The same logic applies to the similar conditional expressions onward.
                isOverrideError -> OptInFixes.HighPriorityPropagateOptInAnnotationFix(targetElement, annotationClassId, kind)

                targetElement.isSubclassOptPropagateApplicable(annotationFqName) -> OptInFixes.PropagateOptInAnnotationFix(
                    targetElement, ClassId.topLevel(OptInNames.SUBCLASS_OPT_IN_REQUIRED_FQ_NAME), kind, annotationFqName
                )

                else -> OptInFixes.PropagateOptInAnnotationFix(targetElement, annotationClassId, kind)
            }
            result.add(quickFix)
        }

        fun collectUseOptInAnnotationFix(targetElement: KtElement, kind: AddAnnotationFix.Kind) {
            val existingAnnotationEntry = (targetElement as? KtAnnotated)?.findAnnotation(optInClassId)?.createSmartPointer()

            val quickFix =
                if (isOverrideError) OptInFixes.UseOptInAnnotationFix(targetElement, optInClassId, kind, annotationFqName, existingAnnotationEntry)
                else OptInFixes.HighPriorityUseOptInAnnotationFix(targetElement, optInClassId, kind, annotationFqName, existingAnnotationEntry)

            result.add(quickFix)
        }

        val candidates = if (element.containingFile.isScript()) element.collectScriptCandidates() else element.collectCandidates()

        candidates.forEach { (targetElement, kind) ->
            collectPropagateOptInAnnotationFix(targetElement, kind)
            collectUseOptInAnnotationFix(targetElement, kind)
        }

        return result
    }

    private fun PsiElement.collectCandidates(): List<CandidateData> {
        val result = mutableListOf<CandidateData>()

        val containingDeclaration: KtDeclaration = this.getParentOfTypesAndPredicate(
            strict = false,
            KtDeclarationWithBody::class.java,
            KtClassOrObject::class.java,
            KtProperty::class.java,
            KtTypeAlias::class.java
        ) {
            !KtPsiUtil.isLocal(it)
        } ?: return emptyList()

        val containingDeclarationCandidate = containingDeclaration.findContainingDeclarationCandidate()
        result.add(containingDeclarationCandidate)
        if (containingDeclaration is KtCallableDeclaration) containingDeclaration.findContainingClassOrObjectCandidate()
            ?.let { result.add(it) }

        return result
    }

    private fun KtElement.collectScriptCandidates(): List<CandidateData> {
        val result = mutableListOf<CandidateData>()
        var current: PsiElement? = this

        fun CandidateData.addToResult() {
            if (result.any { this.element == it.element }) return
            result.add(this)
        }

        val closestDeclaration = this.findParentOfType<KtDeclaration>(strict = false)

        while (current != null) {
            when {
                current is KtClassOrObject && closestDeclaration != current -> current.findContainingClassOrObjectCandidate()?.addToResult()
                current is KtCallExpression -> current.findSamConstructorCallCandidate()?.addToResult()
                current is KtDeclaration &&
                        (current is KtDeclarationWithBody && !current.isLambda()
                                || current is KtTypeAlias
                                || current is KtProperty
                                || current is KtClassOrObject) ->
                    current.findContainingDeclarationCandidate().addToResult()
            }
            current = current.parent
        }

        this.findStatementCandidate()?.addToResult()

        // For the case where two different elements have the same name
        return result.sortedBy { it.kind == AddAnnotationFix.Kind.Self }
    }

    private fun KtDeclarationWithBody.isLambda() = descriptor?.name?.asString() == "<anonymous>"

    private fun KtElement.findStatementCandidate(): CandidateData? {
        require(this.containingFile.isScript())
        var statementElement: KtElement = this
        while (statementElement.parent !is KtBlockExpression && statementElement.parent !is KtClassBody) statementElement =
            statementElement.parent as? KtElement ?: return null
        return CandidateData(statementElement, AddAnnotationFix.Kind.Self)
    }

    private fun KtDeclaration.findContainingDeclarationCandidate(): CandidateData {
        val kind = if (this is KtConstructor<*>) AddAnnotationFix.Kind.Constructor
        else AddAnnotationFix.Kind.Declaration(name)

        return CandidateData(this, kind)
    }

    private fun KtCallExpression.findSamConstructorCallCandidate(): CandidateData? {
        val parent = this.parent
        if (parent !is KtBlockExpression && parent !is KtScriptInitializer && parent !is KtAnnotatedExpression) return null
        val resolvedCall = this.resolveToCall() ?: return null
        if (resolvedCall.resultingDescriptor !is SamConstructorDescriptor) return null
        val element = when {
            parent is KtScriptInitializer -> parent
            parent is KtAnnotatedExpression && parent.parent is KtScriptInitializer -> parent.parent
            else -> this
        }
        val name = resolvedCall.resultingDescriptor.name.asString()
        return CandidateData(element as KtElement, AddAnnotationFix.Kind.Declaration(name))
    }

    private fun KtDeclaration.findContainingClassOrObjectCandidate(): CandidateData? {
        val containingClassOrObject = if (this is KtClassOrObject) this else this.containingClassOrObject ?: return null
        return CandidateData(containingClassOrObject, AddAnnotationFix.Kind.ContainingClass(containingClassOrObject.name))
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

}
