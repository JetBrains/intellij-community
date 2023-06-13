// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.descriptors.Visibilities.Private
import org.jetbrains.kotlin.descriptors.Visibilities.Protected
import org.jetbrains.kotlin.descriptors.Visibilities.Public
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*

private const val JAVA_IO_SERIALIZABLE_CLASS_ID = "java/io/Serializable"
private const val JAVA_IO_SERIALIZATION_READ_RESOLVE = "readResolve"

/**
 * Tests:
 * - [org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SharedK1LocalInspectionTestGenerated.JavaIoSerializableObjectMustHaveReadResolve]
 * - [org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.SharedK2LocalInspectionTestGenerated.JavaIoSerializableObjectMustHaveReadResolve]
 */
class JavaIoSerializableObjectMustHaveReadResolveInspection : AbstractKotlinInspection() {
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
    true == (this@doesImplementSerializable.getSymbol() as? KtClassOrObjectSymbol)
        ?.let(::buildClassType)
        ?.getAllSuperTypes()
        ?.any { it.isClassTypeWithClassId(ClassId.fromString(JAVA_IO_SERIALIZABLE_CLASS_ID)) }
}

private fun KtObjectDeclaration.doesImplementReadResolve(): Boolean = analyze(this) {
    val classSymbol = this@doesImplementReadResolve.getSymbol() as? KtClassOrObjectSymbol ?: return false
    fun KtScope.isAnyReadResolve(vararg visibilities: Visibility): Boolean =
        getCallableSymbols { it.asString() == JAVA_IO_SERIALIZATION_READ_RESOLVE }.any {
            val functionLikeSymbol = it as? KtFunctionLikeSymbol ?: return@any false
            val visibility = (it as? KtSymbolWithVisibility)?.visibility
            visibility in visibilities && functionLikeSymbol.valueParameters.isEmpty() && it.returnType.isAny
        }
    classSymbol.getDeclaredMemberScope().isAnyReadResolve(Public, Private, Protected) ||
            classSymbol.getMemberScope().isAnyReadResolve(Public, Protected)
}
