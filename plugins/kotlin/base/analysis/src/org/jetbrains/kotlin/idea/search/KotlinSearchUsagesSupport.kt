// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue

interface KotlinSearchUsagesSupport {

    interface ConstructorCallHandle {
        fun referencedTo(element: KtElement): Boolean
    }

    companion object {
        fun getInstance(project: Project): KotlinSearchUsagesSupport = project.service()
    }

    object SearchUtils { // not a companion object to load less bytecode simultaneously with KotlinSearchUsagesSupport
        val KtParameter.dataClassComponentMethodName: String?
            get() {
                if (!hasValOrVar() || containingClassOrObject?.hasModifier(KtTokens.DATA_KEYWORD) != true) return null
                return DataClassResolver.createComponentName(parameterIndex() + 1).asString()
            }

        fun <T : PsiNamedElement> List<T>.filterDataClassComponentsIfDisabled(kotlinOptions: KotlinReferencesSearchOptions): List<T> {
            fun PsiNamedElement.isComponentElement(): Boolean {
                if (this !is PsiMethod) return false

                val dataClassParent = ((parent as? KtLightClass)?.kotlinOrigin as? KtClass)?.isData() == true
                if (!dataClassParent) return false

                if (!Name.isValidIdentifier(name)) return false
                val nameIdentifier = Name.identifier(name)
                return DataClassResolver.isComponentLike(nameIdentifier)
            }

            return if (kotlinOptions.searchForComponentConventions) this else filter { !it.isComponentElement() }
        }

        fun PsiNamedElement.getClassNameForCompanionObject(): String? =
            getInstance(project).getClassNameToSearch(this)

        fun PsiReference.isCallableOverrideUsage(declaration: KtNamedDeclaration): Boolean =
            getInstance(declaration.project).isCallableOverrideUsage(this, declaration)

        fun PsiReference.isInvokeOfCompanionObject(declaration: KtNamedDeclaration): Boolean =
            getInstance(declaration.project).isInvokeOfCompanionObject(this, declaration)

        fun PsiReference.isUsageInContainingDeclaration(declaration: KtNamedDeclaration): Boolean =
            getInstance(declaration.project).isUsageInContainingDeclaration(this, declaration)

        fun PsiReference.isUsageOfActual(declaration: KtNamedDeclaration): Boolean =
            getInstance(declaration.project).isUsageOfActual(this, declaration)

        fun PsiReference.isExtensionOfDeclarationClassUsage(declaration: KtNamedDeclaration): Boolean =
            getInstance(declaration.project).isExtensionOfDeclarationClassUsage(this, declaration)

        fun PsiElement.getReceiverTypeSearcherInfo(isDestructionDeclarationSearch: Boolean): ReceiverTypeSearcherInfo? =
            getInstance(project).getReceiverTypeSearcherInfo(this, isDestructionDeclarationSearch)

        fun KtFile.forceResolveReferences(elements: List<KtElement>) =
            getInstance(project).forceResolveReferences(this, elements)

        fun PsiFile.scriptDefinitionExists(): Boolean =
            getInstance(project).scriptDefinitionExists(this)

        fun forEachKotlinOverride(
            ktClass: KtClass,
            members: List<KtNamedDeclaration>,
            scope: SearchScope,
            searchDeeply: Boolean,
            processor: (superMember: PsiElement, overridingMember: PsiElement) -> Boolean
        ): Boolean = getInstance(ktClass.project).forEachKotlinOverride(ktClass, members, scope, searchDeeply, processor)

        fun findDeepestSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> =
            getInstance(method.project).findSuperMethodsNoWrapping(method, true)

        fun findSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> =
            getInstance(method.project).findSuperMethodsNoWrapping(method, false)

        fun KtDeclaration.isOverridable(): Boolean =
            getInstance(project).isOverridable(this)

        @JvmStatic
        fun KtClass.isInheritable(): Boolean =
            getInstance(project).isInheritable(this)

        fun PsiElement.canBeResolvedWithFrontEnd(): Boolean =
            getInstance(project).canBeResolvedWithFrontEnd(this)

        fun createConstructorHandle(ktDeclaration: KtDeclaration): ConstructorCallHandle =
            getInstance(ktDeclaration.project).createConstructorHandle(ktDeclaration)

        fun createConstructorHandle(psiMethod: PsiMethod): ConstructorCallHandle =
            getInstance(psiMethod.project).createConstructorHandle(psiMethod)
    }

    fun isUsageOfActual(reference: PsiReference, declaration: KtNamedDeclaration): Boolean

    fun isInvokeOfCompanionObject(psiReference: PsiReference, searchTarget: KtNamedDeclaration): Boolean

    fun isCallableOverrideUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean

    fun isCallableOverride(subDeclaration: KtDeclaration, superDeclaration: PsiNamedElement): Boolean

    fun isUsageInContainingDeclaration(reference: PsiReference, declaration: KtNamedDeclaration): Boolean

    fun isExtensionOfDeclarationClassUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean

    /**
     *
     * Extract the PSI class for the receiver type of [psiElement] assuming it is an _operator_.
     * Additionally compute an occurence check for uses of the type in another, used to
     * conservatively discard search candidates in which the type does not occur at all.
     *
     * TODO: rename to something more apt? The FE1.0 implementation requires that the target
     *       be an operator.
     */
    fun getReceiverTypeSearcherInfo(psiElement: PsiElement, isDestructionDeclarationSearch: Boolean): ReceiverTypeSearcherInfo?

    fun forceResolveReferences(file: KtFile, elements: List<KtElement>)

    fun scriptDefinitionExists(file: PsiFile): Boolean
    fun findScriptsWithUsages(declaration: KtNamedDeclaration, processor: (KtFile) -> Boolean): Boolean

    fun getDefaultImports(file: KtFile): List<ImportPath>

    fun forEachKotlinOverride(
        ktClass: KtClass,
        members: List<KtNamedDeclaration>,
        scope: SearchScope,
        searchDeeply: Boolean,
        processor: (superMember: PsiElement, overridingMember: PsiElement) -> Boolean
    ): Boolean

    fun findSuperMethodsNoWrapping(method: PsiElement, deepest: Boolean): List<PsiElement>

    fun isOverridable(declaration: KtDeclaration): Boolean

    fun isInheritable(ktClass: KtClass): Boolean

    fun canBeResolvedWithFrontEnd(element: PsiElement): Boolean

    fun createConstructorHandle(ktDeclaration: KtDeclaration): ConstructorCallHandle

    fun createConstructorHandle(psiMethod: PsiMethod): ConstructorCallHandle

    /**
     * Name for companion object or for invoke located in class without constructor
     */
    fun getClassNameToSearch(namedElement : PsiNamedElement): String? =
        (namedElement is KtObjectDeclaration && namedElement.isCompanion())
            .ifTrue { namedElement.getNonStrictParentOfType<KtClass>()?.name }

}