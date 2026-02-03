// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.decompiler.navigation.findDecompiledDeclaration
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.utils.addToStdlib.sequenceOfLazyValues

@K1Deprecation
@Deprecated("Only supported for Kotlin Plugin K1 mode. Use Kotlin Analysis API instead, which works for both K1 and K2 modes. See https://kotl.in/analysis-api and `org.jetbrains.kotlin.analysis.api.analyze` for details.")
@ApiStatus.ScheduledForRemoval
object DescriptorToSourceUtilsIde {
    // Returns PSI element for descriptor. If there are many relevant elements (e.g. it is fake override
    // with multiple declarations), finds any of them. It can find declarations in builtins or decompiled code.
    fun getAnyDeclaration(project: Project, descriptor: DeclarationDescriptor): PsiElement? {
        return getDeclarationsStream(project, descriptor).firstOrNull()
    }

    // Returns all PSI elements for descriptor. It can find declarations in builtins or decompiled code.
    fun getAllDeclarations(
        project: Project,
        targetDescriptor: DeclarationDescriptor,
        builtInsSearchScope: GlobalSearchScope? = null
    ): Collection<PsiElement> {
        val result = getDeclarationsStream(project, targetDescriptor, builtInsSearchScope).toHashSet()
        // filter out elements which are navigate to some other element of the result
        // this is needed to avoid duplicated results for references to declaration in same library source file
        return result.filter { element -> result.none { element != it && it.navigationElement == element } }
    }

    private fun getDeclarationsStream(
        project: Project, targetDescriptor: DeclarationDescriptor, builtInsSearchScope: GlobalSearchScope? = null
    ): Sequence<PsiElement> {
        val effectiveReferencedDescriptors = DescriptorToSourceUtils.getEffectiveReferencedDescriptors(targetDescriptor).asSequence()
        return effectiveReferencedDescriptors.flatMap { effectiveReferenced ->
            // References in library sources should be resolved to corresponding decompiled declarations,
            // therefore we put both source declaration and decompiled declaration to stream, and afterwards we filter it in getAllDeclarations
            sequenceOfLazyValues(
                { DescriptorToSourceUtils.getSourceFromDescriptor(effectiveReferenced) },
                { findDecompiledDeclaration(project, effectiveReferenced, builtInsSearchScope) }
            )
        }.filterNotNull()
    }
}
