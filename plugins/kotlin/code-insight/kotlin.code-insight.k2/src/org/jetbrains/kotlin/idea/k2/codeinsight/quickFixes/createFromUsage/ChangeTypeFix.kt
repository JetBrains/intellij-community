// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.lang.jvm.actions.ChangeTypeRequest
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.load.java.NOT_NULL_ANNOTATIONS
import org.jetbrains.kotlin.load.java.NULLABLE_ANNOTATIONS
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance

internal class ChangeType(
    element: KtCallableDeclaration,
    private val elementContext: ChangeTypeRequest,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtCallableDeclaration, ChangeTypeRequest>(element, elementContext) {

    override fun getPresentation(
        context: ActionContext,
        element: KtCallableDeclaration,
    ): Presentation? = Presentation.of(familyName).takeIf { elementContext.isValid }

    override fun getFamilyName(): @IntentionFamilyName String =
        QuickFixBundle.message("change.type.family")

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallableDeclaration,
        elementContext: ChangeTypeRequest,
        updater: ModPsiUpdater,
    ): Unit = doChangeType(element, elementContext)
}

private fun doChangeType(
    target: KtCallableDeclaration,
    request: ChangeTypeRequest,
) {
    val oldType = target.typeReference
    val typeName = primitiveTypeMapping.getOrDefault(request.qualifiedName, request.qualifiedName ?: typeName(target) ?: return)
    val psiFactory = KtPsiFactory(target.project)
    val annotations = request.annotations.filter { request ->
        FqName(request.qualifiedName) !in (NULLABLE_ANNOTATIONS + NOT_NULL_ANNOTATIONS)
    }.joinToString(" ") { request ->
        "@${renderAnnotation(target, request, psiFactory)}"
    }
    val newType = psiFactory.createType("$annotations $typeName".trim())
    target.typeReference = newType
    if (oldType != null) {
        val commentSaver = CommentSaver(oldType)
        commentSaver.restore(target.typeReference!!)
    }
    shortenReferences(target)
}

@OptIn(KaExperimentalApi::class)
private fun typeName(declaration: KtCallableDeclaration): String? {
    val typeReference = declaration.typeReference
    if (typeReference != null) return typeReference.typeElement?.text
    if ((declaration !is KtNamedFunction) && (declaration !is KtProperty)) return null
    return allowAnalysisFromWriteActionInEdt(declaration) {
        analyze(declaration) {
            val symbol = declaration.symbol as? KaCallableSymbol ?: return null
            val returnType = symbol.returnType
            returnType.render(position = Variance.IN_VARIANCE)
        }
    }
}

private val primitiveTypeMapping: Map<String, String> = mapOf(
    PsiTypes.voidType().name to "kotlin.Unit",
    PsiTypes.booleanType().name to "kotlin.Boolean",
    PsiTypes.byteType().name to "kotlin.Byte",
    PsiTypes.charType().name to "kotlin.Char",
    PsiTypes.shortType().name to "kotlin.Short",
    PsiTypes.intType().name to "kotlin.Int",
    PsiTypes.floatType().name to "kotlin.Float",
    PsiTypes.longType().name to "kotlin.Long",
    PsiTypes.doubleType().name to "kotlin.Double",
    "${PsiTypes.booleanType().name}[]" to "kotlin.BooleanArray",
    "${PsiTypes.byteType().name}[]" to "kotlin.ByteArray",
    "${PsiTypes.charType().name}[]" to "kotlin.CharArray",
    "${PsiTypes.shortType().name}[]" to "kotlin.ShortArray",
    "${PsiTypes.intType().name}[]" to "kotlin.IntArray",
    "${PsiTypes.floatType().name}[]" to "kotlin.FloatArray",
    "${PsiTypes.longType().name}[]" to "kotlin.LongArray",
    "${PsiTypes.doubleType().name}[]" to "kotlin.DoubleArray"
)
