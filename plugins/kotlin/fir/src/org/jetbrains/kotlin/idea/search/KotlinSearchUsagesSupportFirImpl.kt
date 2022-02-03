/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.classes.KtFakeLightMethod
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.core.isInheritable
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.analyse
import org.jetbrains.kotlin.idea.frontend.api.analyseWithReadAction
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.idea.frontend.api.tokens.HackToForceAllowRunningAnalyzeOnEDT
import org.jetbrains.kotlin.idea.frontend.api.tokens.hackyAllowRunningOnEdt
import org.jetbrains.kotlin.idea.search.declarationsSearch.toPossiblyFakeLightMethods
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.usagesSearch.getDefaultImports
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.ImportPath

class KotlinSearchUsagesSupportFirImpl : KotlinSearchUsagesSupport {
    override fun actualsForExpected(declaration: KtDeclaration, module: Module?): Set<KtDeclaration> {
        return emptySet()
    }

    override fun dataClassComponentMethodName(element: KtParameter): String? {
        return null
    }

    override fun hasType(element: KtExpression): Boolean {
        return false
    }

    override fun isSamInterface(psiClass: PsiClass): Boolean {
        return false
    }

    override fun <T : PsiNamedElement> filterDataClassComponentsIfDisabled(
        elements: List<T>,
        kotlinOptions: KotlinReferencesSearchOptions
    ): List<T> {
        return emptyList()
    }

    override fun isCallableOverrideUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        return false
    }

    override fun isUsageInContainingDeclaration(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        return false
    }


    override fun isExtensionOfDeclarationClassUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        return false
    }

    override fun getReceiverTypeSearcherInfo(psiElement: PsiElement, isDestructionDeclarationSearch: Boolean): ReceiverTypeSearcherInfo? {
        return null
    }

    override fun forceResolveReferences(file: KtFile, elements: List<KtElement>) {

    }

    override fun scriptDefinitionExists(file: PsiFile): Boolean {
        return false
    }

    override fun getDefaultImports(file: KtFile): List<ImportPath> {
        return file.getDefaultImports()
    }

    override fun forEachKotlinOverride(
        ktClass: KtClass,
        members: List<KtNamedDeclaration>,
        scope: SearchScope,
        searchDeeply: Boolean,
        processor: (superMember: PsiElement, overridingMember: PsiElement) -> Boolean
    ): Boolean {
        return false
    }

    override fun findSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> {
        return emptyList()
    }

    override fun forEachOverridingMethod(method: PsiMethod, scope: SearchScope, processor: (PsiMethod) -> Boolean): Boolean {
        if (!findNonKotlinMethodInheritors(method, scope, processor)) return false

        return findKotlinInheritors(method, scope, processor)
    }

    @OptIn(HackToForceAllowRunningAnalyzeOnEDT::class)
    private fun findKotlinInheritors(
        method: PsiMethod,
        scope: SearchScope,
        processor: (PsiMethod) -> Boolean
    ): Boolean {
        val ktMember = method.unwrapped as? KtNamedDeclaration ?: return true
        val ktClass = runReadAction { ktMember.containingClassOrObject as? KtClass } ?: return true

        return DefinitionsScopedSearch.search(ktClass, scope, true).forEach(Processor { psiClass ->
            val inheritor = psiClass.unwrapped as? KtClassOrObject ?: return@Processor true
            hackyAllowRunningOnEdt {
                analyseWithReadAction(inheritor) {
                    findMemberInheritors(ktMember, inheritor, processor)
                }
            }
        })
    }

    private fun KtAnalysisSession.findMemberInheritors(
        superMember: KtNamedDeclaration,
        targetClass: KtClassOrObject,
        processor: (PsiMethod) -> Boolean
    ): Boolean {
        val originalMemberSymbol = superMember.getSymbol()
        val inheritorSymbol = targetClass.getClassOrObjectSymbol()
        val inheritorMembers = inheritorSymbol.getDeclaredMemberScope()
            .getCallableSymbols { it == superMember.nameAsSafeName }
            .filter { candidate ->
                // todo find a cheaper way
                candidate.getAllOverriddenSymbols().any { it == originalMemberSymbol }
            }
        for (member in inheritorMembers) {
            val lightInheritorMembers = member.psi?.toPossiblyFakeLightMethods()?.distinctBy { it.unwrapped }.orEmpty()
            for (lightMember in lightInheritorMembers) {
                if (!processor(lightMember)) {
                    return false
                }
            }
        }
        return true
    }

    private fun findNonKotlinMethodInheritors(
        method: PsiMethod,
        scope: SearchScope,
        processor: (PsiMethod) -> Boolean
    ): Boolean {
        if (method !is KtFakeLightMethod) {
            val query = OverridingMethodsSearch.search(method, scope.excludeKotlinSources(), true)
            val continueSearching = query.forEach(Processor { processor(it) })
            if (!continueSearching) {
                return false
            }
        }
        return true
    }

    override fun findDeepestSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> {
        return when (val element = method.unwrapped) {
            is PsiMethod -> element.findDeepestSuperMethods().toList()
            is KtCallableDeclaration -> analyse(element) {
                val symbol = element.getSymbol() as? KtCallableSymbol ?: return emptyList()
                symbol.getAllOverriddenSymbols()
                    .filter {
                        when (it) {
                            is KtFunctionSymbol -> it.isOverride
                            is KtPropertySymbol -> it.isOverride
                            else -> false
                        }
                    }.mapNotNull { it.psi }
            }
            else -> emptyList()
        }
    }

    override fun findTypeAliasByShortName(shortName: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> {
        return KotlinTypeAliasShortNameIndex.getInstance().get(shortName, project, scope)
    }

    override fun isInProjectSource(element: PsiElement, includeScriptsOutsideSourceRoots: Boolean): Boolean {
        return ProjectRootsUtil.isInProjectSource(element, includeScriptsOutsideSourceRoots)
    }

    override fun isOverridable(declaration: KtDeclaration): Boolean {
        val parent = declaration.parent
        if (!(parent is KtClassBody || parent is KtParameterList)) return false

        val klass = if (parent.parent is KtPrimaryConstructor)
            parent.parent.parent as? KtClass
        else
            parent.parent as? KtClass

        if (klass == null || (!klass.isInheritable() && !klass.isEnum())) return false

        if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
            // 'private' is incompatible with 'open'
            return false
        }

        return isOverridableBySymbol(declaration)
    }

    override fun isInheritable(ktClass: KtClass): Boolean = isOverridableBySymbol(ktClass)

    private fun isOverridableBySymbol(declaration: KtDeclaration) = analyseWithReadAction(declaration) {
        val symbol = declaration.getSymbol() as? KtSymbolWithModality ?: return@analyseWithReadAction false
        when (symbol.modality) {
            Modality.OPEN, Modality.SEALED, Modality.ABSTRACT -> true
            Modality.FINAL -> false
        }
    }

    override fun formatJavaOrLightMethod(method: PsiMethod): String {
        return "FORMAT JAVA OR LIGHT METHOD ${method.name}"
    }

    override fun formatClass(classOrObject: KtClassOrObject): String {
        return "FORMAT CLASS ${classOrObject.name}"
    }

    override fun expectedDeclarationIfAny(declaration: KtDeclaration): KtDeclaration? {
        return null
    }

    override fun isExpectDeclaration(declaration: KtDeclaration): Boolean {
        return false
    }

    override fun canBeResolvedWithFrontEnd(element: PsiElement): Boolean {
        //TODO FIR: Is the same as PsiElement.hasJavaResolutionFacade() as for FIR?
        return element.originalElement.containingFile != null
    }

    override fun createConstructorHandle(ktDeclaration: KtDeclaration): KotlinSearchUsagesSupport.ConstructorCallHandle {
        //TODO FIR: This is the stub. Need to implement
        return object : KotlinSearchUsagesSupport.ConstructorCallHandle {
            override fun referencedTo(element: KtElement): Boolean {
                return false
            }
        }
    }

    override fun createConstructorHandle(psiMethod: PsiMethod): KotlinSearchUsagesSupport.ConstructorCallHandle {
        //TODO FIR: This is the stub. Need to implement
        return object : KotlinSearchUsagesSupport.ConstructorCallHandle {
            override fun referencedTo(element: KtElement): Boolean {
                return false
            }
        }
    }
}