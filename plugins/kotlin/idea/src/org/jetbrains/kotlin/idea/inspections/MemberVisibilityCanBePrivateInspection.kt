// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.EntryPointsManager
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.Processor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.canBePrivate
import org.jetbrains.kotlin.idea.core.isInheritable
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.isSpecial
import org.jetbrains.kotlin.idea.search.isCheapEnoughToSearchConsideringOperators
import org.jetbrains.kotlin.idea.search.usagesSearch.dataClassComponentFunction
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addIfNotNull

class MemberVisibilityCanBePrivateInspection : AbstractKotlinInspection() {

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
        if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) || declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
        if (KotlinPsiHeuristics.hasNonSuppressAnnotations(declaration)) return false

        val classOrObject = declaration.containingClassOrObject ?: return false
        val inheritable = classOrObject is KtClass && classOrObject.isInheritable()
        if (!inheritable && declaration.hasModifier(KtTokens.PROTECTED_KEYWORD)) return false //reported by ProtectedInFinalInspection
        if (declaration.isOverridable()) return false

        val descriptor = (declaration.toDescriptor() as? DeclarationDescriptorWithVisibility) ?: return false
        when (descriptor.effectiveVisibility()) {
            EffectiveVisibility.PrivateInClass, EffectiveVisibility.PrivateInFile, EffectiveVisibility.Local -> return false
            else -> {}
        }

        val entryPointsManager = EntryPointsManager.getInstance(declaration.project) as EntryPointsManagerBase
        if (UnusedSymbolInspection.checkAnnotatedUsingPatterns(
                declaration,
                with(entryPointsManager) {
                    additionalAnnotations + ADDITIONAL_ANNOTATIONS
                }
            )
        ) return false

        if (!declaration.canBePrivate()) return false

        // properties can be referred by component1/component2, which is too expensive to search, don't analyze them
        if (declaration is KtParameter && declaration.dataClassComponentFunction() != null) return false

        val psiSearchHelper = PsiSearchHelper.getInstance(declaration.project)
        val useScope = declaration.useScope
        val name = declaration.name ?: return false
        val restrictedScope = if (useScope is GlobalSearchScope) {
            when (psiSearchHelper.isCheapEnoughToSearchConsideringOperators(name, useScope)) {
                PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES -> return false
                PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES -> return false
                PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES -> KotlinSourceFilterScope.projectSourcesAndResources(useScope, declaration.project)
            }
        } else useScope

        val usageDeclarations = mutableListOf<KtDeclaration>()
        var otherUsageFound = false
        var inClassUsageFound = false
        ReferencesSearch.search(declaration, restrictedScope).forEach(Processor {
            val usage = it.element
            usageDeclarations.addIfNotNull(usage.parentOfTypes(KtProperty::class, KtFunction::class))
            if (usage.isOutside(classOrObject)) {
                otherUsageFound = true
                return@Processor false
            }
            val classOrObjectDescriptor = classOrObject.descriptor as? ClassDescriptor
            if (classOrObjectDescriptor != null) {
                val receiverType = (usage as? KtElement)?.resolveToCall()?.dispatchReceiver?.type
                val receiverDescriptor = receiverType?.constructor?.declarationDescriptor
                if (receiverDescriptor != null && receiverDescriptor != classOrObjectDescriptor) {
                    otherUsageFound = true
                    return@Processor false
                }
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
            } else {
                inClassUsageFound = true
                true
            }
        })

        if (!inClassUsageFound || otherUsageFound) return false

        return !declaration.typeWillBeExposed(usageDeclarations, classOrObject)
    }

    private fun PsiElement.isOutside(classOrObject: KtClassOrObject): Boolean {
        if (classOrObject != getParentOfType<KtClassOrObject>(false)) return true
        val annotationEntry = getStrictParentOfType<KtAnnotationEntry>() ?: return false
        return classOrObject.annotationEntries.any { it == annotationEntry }
    }

    private fun KtModifierListOwner?.insideInline() = this?.let { it.hasModifier(KtTokens.INLINE_KEYWORD) && !it.isPrivate() } ?: false

    private fun KtDeclaration.typeReference(): KtTypeReference? = (this as? KtCallableDeclaration)?.typeReference

    private fun KtDeclaration.initializer(): KtExpression? =
        (this as? KtProperty)?.getter?.initializer ?: (this as? KtDeclarationWithInitializer)?.initializer

    private fun KtDeclaration.isLocal(): Boolean = (this as? KtProperty)?.isLocal == true || (this as? KtFunction)?.isLocal == true

    private fun List<KtDeclaration>.indexOfOrNull(e: KtDeclaration): Int? = indexOf(e).takeIf { it != -1 }

    private fun KotlinType.hasSpecial(): Boolean = arguments.any { it.type.isSpecial() || it.type.hasSpecial() }

    private fun KtDeclaration.typeWillBeExposed(
        usageDeclarations: List<KtDeclaration>,
        containingDeclaration: KtClassOrObject
    ): Boolean {
        if (typeReference() != null || initializer() !is KtObjectLiteralExpression) return false

        val members = containingDeclaration.declarations
        val selfIndex = members.indexOfOrNull(this) ?: return false
        val memberIndicesThatMayBeExposed = usageDeclarations.mapNotNull {
            if (it.isLocal() || it.isPrivate() || it.typeReference() != null || it.initializer() == null) null else members.indexOfOrNull(it)
        }.toSet().ifEmpty { return false }

        val copied = containingDeclaration.copied()
        val copiedMembers = copied.declarations
        copiedMembers[selfIndex].addModifier(KtTokens.PRIVATE_KEYWORD)
        val context = copied.analyzeAsReplacement(containingDeclaration, containingDeclaration.analyze(BodyResolveMode.PARTIAL))
        for ((index, copiedMember) in copiedMembers.withIndex()) {
            if (index in memberIndicesThatMayBeExposed && copiedMember.initializer()?.getType(context)?.hasSpecial() == true) return true
        }

        return false
    }

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
            IntentionWrapper(AddModifierFixFE10(modifierListOwner, KtTokens.PRIVATE_KEYWORD).asIntention())
        )
    }
}
