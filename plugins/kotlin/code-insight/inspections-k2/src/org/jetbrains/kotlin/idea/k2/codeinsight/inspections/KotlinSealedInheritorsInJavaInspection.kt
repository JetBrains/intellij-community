// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Reports attempts to inherit from Kotlin sealed interfaces or classes in Java code.
 */
class KotlinSealedInheritorsInJavaInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (aClass is PsiTypeParameter) return
                aClass.listSealedParentReferences().forEach {
                    holder.registerProblem(
                        it,
                        KotlinBundle.message("inheritance.of.kotlin.sealed", 0.takeIf { aClass.isInterface } ?: 1),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                }
            }
        }
    }
}

private fun PsiClass.listSealedParentReferences(): List<PsiReference> {
    if (this is PsiAnonymousClass && baseClassType.isKotlinSealedK2())
        return listOf(baseClassReference)

    val sealedBaseClasses = extendsList?.listSealedMembersK2()
    val sealedBaseInterfaces = implementsList?.listSealedMembersK2()

    return sealedBaseClasses.orEmpty() + sealedBaseInterfaces.orEmpty()
}

private fun PsiReferenceList.listSealedMembersK2(): List<PsiReference> = referencedTypes
    .filter { it.isKotlinSealedK2() }
    .mapNotNull { it as? PsiClassReferenceType }
    .map { it.reference }

private fun PsiClassType.isKotlinSealedK2(): Boolean = resolve()?.isKotlinSealedK2() == true

private fun PsiClass.isKotlinSealedK2(): Boolean {
    val light = this as? KtLightClass ?: return false
    val origin: KtClassOrObject = light.kotlinOrigin ?: return false
    return analyze(origin) {
        val symbol = origin.symbol as? KaClassLikeSymbol ?: return@analyze false
        symbol.modality == KaSymbolModality.SEALED
    }
}