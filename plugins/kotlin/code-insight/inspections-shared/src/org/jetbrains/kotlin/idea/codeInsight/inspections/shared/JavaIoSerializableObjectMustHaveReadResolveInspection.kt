// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

private val JAVA_IO_SERIALIZABLE_CLASS_ID = ClassId.fromString("java/io/Serializable")
private val JAVA_IO_SERIALIZATION_READ_RESOLVE = Name.identifier("readResolve")

/**
 * Tests:
 * - [org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SharedK1LocalInspectionTestGenerated.JavaIoSerializableObjectMustHaveReadResolve]
 * - [org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.SharedK2LocalInspectionTestGenerated.JavaIoSerializableObjectMustHaveReadResolve]
 */
internal class JavaIoSerializableObjectMustHaveReadResolveInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = MyVisitor(holder)

    private class MyVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
        override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
            if (!declaration.isObjectLiteral() && declaration.doesImplementSerializable() && !declaration.doesImplementReadResolve()) {
                holder.registerProblem(
                    declaration.nameIdentifier ?: return,
                    KotlinBundle.message("inspection.java.io.serializable.object.must.have.read.resolve.warning"),
                    ImplementReadResolveQuickFix()
                )
            }
        }
    }
}

private class ImplementReadResolveQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.java.io.serializable.object.must.have.read.resolve.quick.fix.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val objectDeclaration =
            (descriptor.psiElement as? LeafPsiElement)?.let { it.parent as? KtObjectDeclaration } ?: return
        val readResolveDeclaration =
            KtPsiFactory(project).createDeclarationByPattern<KtFunction>("private fun readResolve(): Any = $0", objectDeclaration.name ?: return)
        val body = objectDeclaration.getOrCreateBody()
        body.addAfter(readResolveDeclaration, body.lBrace)
    }
}

private fun KtObjectDeclaration.doesImplementSerializable(): Boolean = analyze(this) {
    buildClassType(symbol).isSubtypeOf(JAVA_IO_SERIALIZABLE_CLASS_ID)
}

private fun KtObjectDeclaration.doesImplementReadResolve(): Boolean = analyze(this) {
    val classSymbol = this@doesImplementReadResolve.symbol
    fun KaScope.isAnyReadResolve(vararg visibilities: KaSymbolVisibility): Boolean =
        callables(JAVA_IO_SERIALIZATION_READ_RESOLVE).any {
            val functionLikeSymbol = it as? KaFunctionSymbol ?: return@any false
            functionLikeSymbol.valueParameters.isEmpty() && it.visibility in visibilities && it.returnType.isAnyType
        }

    classSymbol.declaredMemberScope.isAnyReadResolve(KaSymbolVisibility.PUBLIC, KaSymbolVisibility.PRIVATE, KaSymbolVisibility.PROTECTED) ||
            classSymbol.memberScope.isAnyReadResolve(KaSymbolVisibility.PUBLIC, KaSymbolVisibility.PROTECTED)
}
