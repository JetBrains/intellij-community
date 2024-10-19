// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.EntryPointsManager
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.canBePrivate
import org.jetbrains.kotlin.idea.codeinsight.utils.isInheritable
import org.jetbrains.kotlin.idea.codeinsight.utils.toVisibility
import org.jetbrains.kotlin.idea.highlighting.K2UnusedSymbolUtil
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.idea.search.isCheapEnoughToSearchConsideringOperators
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

class K2MemberVisibilityCanBePrivateInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)
                if (!property.isLocal && canBePrivate(property)) {
                    registerProblem(holder, property)
                }
            }

            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                if (canBePrivate(function)) {
                    registerProblem(holder, function)
                }
            }

            override fun visitParameter(parameter: KtParameter) {
                super.visitParameter(parameter)
                if (parameter.isConstructorDeclaredProperty() && canBePrivate(parameter)) {
                    registerProblem(holder, parameter)
                }
            }
        }
    }

    private fun canBePrivate(declaration: KtNamedDeclaration): Boolean {
        analyze(declaration){
        if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) || declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
        if (KotlinPsiHeuristics.hasNonSuppressAnnotations(declaration)) return false

        val containingClassOrObject = declaration.containingClassOrObject ?: return false
        val inheritable = containingClassOrObject is KtClass && containingClassOrObject.isInheritable()
        if (!inheritable && declaration.hasModifier(KtTokens.PROTECTED_KEYWORD)) return false //reported by ProtectedInFinalInspection
        if (declaration.isOverridable()) return false
        if (declaration.hasModifier(KtTokens.EXTERNAL_KEYWORD)) return false

        if (isSemiEffectivePrivateOrLocal(declaration)) return false

        val entryPointsManager = EntryPointsManager.getInstance(declaration.project) as EntryPointsManagerBase
        if (K2UnusedSymbolUtil.checkAnnotatedUsingPatterns(
                declaration,
                with(entryPointsManager) {
                    additionalAnnotations + ADDITIONAL_ANNOTATIONS
                }
            )
        ) return false

        if (!declaration.canBePrivate()) return false

        // properties can be referred by component1/component2, which is too expensive to search, don't analyze them
        //if (declaration is KtParameter && declaration.dataClassComponentFunction() != null) return false

        val psiSearchHelper = PsiSearchHelper.getInstance(declaration.project)
        val useScope = declaration.useScope
        val name = declaration.name ?: return false
        val restrictedScope = if (useScope is GlobalSearchScope) {
            when (psiSearchHelper.isCheapEnoughToSearchConsideringOperators(name, useScope)) {
                PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES -> return false
                PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES -> return false
                PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES -> KotlinSourceFilterScope.Companion.projectSourcesAndResources(useScope, declaration.project)
            }
        } else useScope

        var otherUsageFound = false
        var inClassUsageFound = false
        ReferencesSearch.search(declaration, restrictedScope).forEach(Processor {
            val usage = it.element
            if (usage.isOutside(containingClassOrObject)) {
                otherUsageFound = true
                return@Processor false
            }
            val receiverType = ((usage as? KtElement)?.resolveToCall()?.successfulCallOrNull<KaCall>() as? KaCallableMemberCall<*,*>)?.partiallyAppliedSymbol?.dispatchReceiver?.type?.expandedSymbol?.psi
            if (receiverType != null && receiverType != containingClassOrObject) {
                otherUsageFound = true
                return@Processor false
            }
            // Do not privatize functions referenced by callable references
            if (usage.getStrictParentOfType<KtCallableReferenceExpression>() != null) {
                // Consider the reference is used outside of the class,
                // as KFunction#call would fail even on references inside that same class
                otherUsageFound = true
                return@Processor false
            }
            val function = usage.getParentOfTypesAndPredicate<KtDeclarationWithBody>(
                true, KtNamedFunction::class.java, KtPropertyAccessor::class.java
            ) { true }
            val insideInlineFun = function.insideInline() || (function as? KtPropertyAccessor)?.property.insideInline()
            if (insideInlineFun) {
                otherUsageFound = true
                false
            }
            else {
                inClassUsageFound = true
                true
            }
        })

        return inClassUsageFound && !otherUsageFound

        }
    }

    private fun isSemiEffectivePrivateOrLocal(declaration: KtNamedDeclaration): Boolean {
        var d = declaration
        while (d !is KtFile) {
            val visibility = d.visibilityModifierTypeOrDefault().toVisibility()
            if (Visibilities.isPrivate(visibility)
                || visibility == Visibilities.Local
                || d is KtClass && d.isLocal
                || d is KtObjectDeclaration && d.isObjectLiteral()) {
                return true
            };
            d = d.containingClassOrObject ?: return false
        }
        return false
    }

    private fun PsiElement.isOutside(classOrObject: KtClassOrObject): Boolean {
        if (classOrObject != getParentOfType<KtClassOrObject>(false)) return true
        val annotationEntry = getStrictParentOfType<KtAnnotationEntry>() ?: return false
        return classOrObject.annotationEntries.any { it == annotationEntry }
    }

    private fun KtModifierListOwner?.insideInline() = this?.let { it.hasModifier(KtTokens.INLINE_KEYWORD) && !it.isPrivate() } ?: false

    private fun registerProblem(holder: ProblemsHolder, declaration: KtDeclaration) {
        val modifierListOwner = declaration.getParentOfType<KtModifierListOwner>(false) ?: return
        val member = when (declaration) {
            is KtNamedFunction -> KotlinBundle.message("text.Function")
            else -> KotlinBundle.message("text.Property")
        }

        val nameElement = (declaration as? PsiNameIdentifierOwner)?.nameIdentifier ?: return
        holder.registerProblem(
            declaration.visibilityModifier() ?: nameElement,
            KotlinBundle.message("0.1.could.be.private", member, declaration.getName().toString()),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            IntentionWrapper(AddPrivateModifierFix(modifierListOwner).asIntention())
        )
    }

    private class AddPrivateModifierFix(property: KtModifierListOwner) : AddModifierFix(property, KtTokens.PRIVATE_KEYWORD) {
        override fun startInWriteAction(): Boolean = false

        override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
            val property = element ?: return
            runWriteActionIfPhysical(property) {
                property.addModifier(modifier)
            }
        }
    }
}
