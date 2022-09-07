// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.api.KtStarProjectionTypeArgument
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.api.types.KtDynamicType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.codeinsight.utils.isPublicApi
import org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.isNull
import javax.swing.JComponent

fun KtType.isFlexibleRecursive(): Boolean {
    if (this is KtFlexibleType) return true
    if (this !is KtNonErrorClassType) return false
    return typeArguments.any { it !is KtStarProjectionTypeArgument && it.type?.isFlexibleRecursive() == true }
}

fun dangerousFlexibleTypeOrNull(declaration: KtCallableDeclaration, publicAPIOnly: Boolean, reportPlatformArguments: Boolean): KtType? {
    when (declaration) {
        is KtFunction -> if (declaration.isLocal || declaration.hasDeclaredReturnType()) return null
        is KtProperty -> if (declaration.isLocal || declaration.typeReference != null) return null
        else -> return null
    }

    if (declaration.containingClassOrObject?.isLocal == true) return null

    if (publicAPIOnly && !isPublicApi(declaration)) return null
    val type = analyze(declaration) {
        declaration.getReturnKtType()
    }
    if (type is KtDynamicType) return null
    if (reportPlatformArguments) {
        if (!type.isFlexibleRecursive()) return null
    } else {
        if (type !is KtFlexibleType) return null
    }
    return type
}

fun KtCallableDeclaration.getNonNullableReturnType(): KtType =
    analyze(this) { this@getNonNullableReturnType.getReturnKtType().withNullability(KtTypeNullability.NON_NULLABLE) }

fun getAddExclExclCallFix(element: PsiElement?, checkImplicitReceivers: Boolean = false): AddExclExclCallFix? {
    fun KtExpression?.asFix(implicitReceiver: Boolean = false) = this?.let { AddExclExclCallFix(it, implicitReceiver) }

    val psiElement = element ?: return null
    if ((psiElement as? KtExpression)?.isNull() == true) {
        return null
    }
    if (psiElement is LeafPsiElement && psiElement.elementType == KtTokens.DOT) {
        return (psiElement.prevSibling as? KtExpression).asFix()
    }
    return when (psiElement) {
        is KtArrayAccessExpression -> psiElement.asFix()
        is KtOperationReferenceExpression -> {
            when (val parent = psiElement.parent) {
                is KtUnaryExpression -> parent.baseExpression.asFix()
                is KtBinaryExpression -> {
                    val receiver = if (KtPsiUtil.isInOrNotInOperation(parent)) parent.right else parent.left
                    receiver.asFix()
                }

                else -> null
            }
        }

        is KtExpression -> {
            val parent = psiElement.parent
            analyze(psiElement) {
                psiElement.getPossiblyQualifiedCallExpression()?.getImplicitReceiverSmartCast()
            }
            null/*
            val context = psiElement.analyze()

            if (checkImplicitReceivers && psiElement.getResolvedCall(context)?.getImplicitReceiverValue() is ExtensionReceiver) {
                val expressionToReplace = parent as? KtCallExpression ?: parent as? KtCallableReferenceExpression ?: psiElement
                expressionToReplace.asFix(implicitReceiver = true)
            } else {
                val targetElement = parent.safeAs<KtCallableReferenceExpression>()?.receiverExpression ?: psiElement
                context[BindingContext.EXPRESSION_TYPE_INFO, targetElement]?.let {
                    val type = it.type

                    val dataFlowValueFactory = targetElement.getResolutionFacade().dataFlowValueFactory

                    if (type != null) {
                        val nullability = it.dataFlowInfo.getStableNullability(
                            dataFlowValueFactory.createDataFlowValue(targetElement, type, context, targetElement.findModuleDescriptor())
                        )
                        if (!nullability.canBeNonNull()) return null
                    }
                }
                targetElement.asFix()
            }

             */
        }

        else -> null
    }
}

class HasPlatformTypeInspection(
    @JvmField var publicAPIOnly: Boolean = true, @JvmField var reportPlatformArguments: Boolean = false
) : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            val declaration = element as? KtCallableDeclaration ?: return
            val type = dangerousFlexibleTypeOrNull(declaration, publicAPIOnly, reportPlatformArguments) ?: return
            if (type.nullability != KtTypeNullability.NULLABLE) return

            val fixes = SmartList<LocalQuickFix>()
            val expression = declaration.node.findChildByType(KtTokens.EQ)?.psi?.getNextSiblingIgnoringWhitespaceAndComments()
            if (expression != null && (!reportPlatformArguments || !declaration.getNonNullableReturnType().isFlexibleRecursive())) {
                getAddExclExclCallFix(expression)?.let { fixes.add(IntentionWrapper(it)) }
            }
            if (fixes.isEmpty()) return

            holder.registerProblemWithoutOfflineInformation(
                declaration, KotlinBundle.message(
                    "declaration.has.type.inferred.from.a.platform.call.which.can.lead.to.unchecked.nullability.issues"
                ), isOnTheFly, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *fixes.toTypedArray()
            )
        }
    }

    private fun fixText(declaration: KtCallableDeclaration): String = when (declaration) {
        is KtFunction -> KotlinBundle.message("specify.return.type.explicitly")
        else -> KotlinBundle.message("specify.type.explicitly")
    }

    override fun createOptionsPanel(): JComponent {
        val panel = MultipleCheckboxOptionsPanel(this)
        panel.addCheckbox(KotlinBundle.message("apply.only.to.public.or.protected.members"), "publicAPIOnly")
        panel.addCheckbox(KotlinBundle.message("report.for.types.with.platform.arguments"), "reportPlatformArguments")
        return panel
    }
}