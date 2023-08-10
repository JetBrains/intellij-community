/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.references

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.references.fe10.base.KtFe10ReferenceResolutionHelper
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyze
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KtFe10ReferenceResolutionHelperImpl : KtFe10ReferenceResolutionHelper {
    override fun findDecompiledDeclaration(
        project: Project,
        referencedDescriptor: DeclarationDescriptor,
        builtInsSearchScope: GlobalSearchScope?
    ): KtDeclaration? =
        org.jetbrains.kotlin.idea.decompiler.navigation.findDecompiledDeclaration(project, referencedDescriptor, builtInsSearchScope)

    override fun findPsiDeclarations(
        declaration: DeclarationDescriptor,
        project: Project,
        resolveScope: GlobalSearchScope
    ): Collection<PsiElement> = declaration.findPsiDeclarations(project, resolveScope)

    override fun isInProjectOrLibSource(element: PsiElement, includeScriptsOutsideSourceRoots: Boolean): Boolean {
        return RootKindFilter.projectAndLibrarySources
            .copy(includeScriptsOutsideSourceRoots = includeScriptsOutsideSourceRoots)
            .matches(element)
    }

    override fun partialAnalyze(element: KtElement): BindingContext = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)

    override fun resolveImportReference(file: KtFile, fqName: FqName): Collection<DeclarationDescriptor> =
        file.resolveImportReference(fqName)

    override fun resolveKDocLink(element: KDocName): Collection<DeclarationDescriptor> {
        val declaration = element.getContainingDoc().owner ?: return emptyList()
        val resolutionFacade = element.getResolutionFacade()
        val correctContext = declaration.safeAnalyze(resolutionFacade, BodyResolveMode.PARTIAL)
        if (correctContext == BindingContext.EMPTY) return emptyList()
        val declarationDescriptor = correctContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration] ?: return emptyList()

        val kdocLink = element.getStrictParentOfType<KDocLink>()!!
        return org.jetbrains.kotlin.idea.kdoc.resolveKDocLink(
            correctContext,
            resolutionFacade,
            declarationDescriptor,
            element,
            kdocLink.getTagIfSubject(),
            element.getQualifiedName()
        )
    }
}