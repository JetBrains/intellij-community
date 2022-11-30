// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.KtSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.codeInsight.CallTarget
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.process
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.parents

internal class KotlinRecursiveCallLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        KotlinCallProcessor.process(elements) { target ->
            val symbol = target.symbol
            val targetDeclaration = target.symbol.psi as? KtDeclaration ?: return@process

            if (symbol.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED || !targetDeclaration.isAncestor(target.caller)) {
                return@process
            }

            if (isRecursiveCall(target, targetDeclaration)) {
                @NlsSafe val declarationName = when (symbol) {
                    is KtVariableLikeSymbol -> symbol.name.asString()
                    is KtFunctionSymbol -> symbol.name.asString() + "()"
                    is KtPropertyGetterSymbol -> "get()"
                    is KtPropertySetterSymbol -> "set()"
                    is KtConstructorSymbol -> "constructor()"
                    else -> return@process
                }

                val message = KotlinLineMarkersBundle.message("line.markers.recursive.call.description")
                result += RecursiveCallLineMarkerInfo(target.anchorLeaf, message, declarationName, targetDeclaration.createSmartPointer())
            }
        }
    }

    private fun KtAnalysisSession.isRecursiveCall(target: CallTarget, targetDeclaration: PsiElement): Boolean {
        for (parent in target.caller.parents) {
            when (parent) {
                targetDeclaration -> return checkDispatchReceiver(target)
                is KtPropertyAccessor -> {} // Skip, handle in 'KtProperty'
                is KtProperty -> if (!parent.isLocal) return false
                is KtDestructuringDeclaration -> {} // Skip, destructuring declaration is not a scoping declaration
                is KtFunctionLiteral -> {} // Count calls inside lambdas
                is KtNamedFunction -> if (parent.nameIdentifier != null) return false // Count calls inside anonymous functions
                is KtObjectDeclaration -> if (!parent.isObjectLiteral()) return false
                is KtDeclaration -> return false
            }
        }

        return false
    }

    private fun KtAnalysisSession.checkDispatchReceiver(target: CallTarget): Boolean {
        var dispatchReceiver = target.partiallyAppliedSymbol.dispatchReceiver ?: return true
        while (dispatchReceiver is KtSmartCastedReceiverValue) {
            dispatchReceiver = dispatchReceiver.original
        }

        val containingClass = target.symbol.getContainingSymbol() as? KtClassOrObjectSymbol ?: return true

        if (dispatchReceiver is KtExplicitReceiverValue) {
            if (dispatchReceiver.isSafeNavigation) {
                return false
            }

            return when (val expression = KtPsiUtil.deparenthesize(dispatchReceiver.expression)) {
                is KtThisExpression -> expression.instanceReference.mainReference.resolveToSymbol() == containingClass
                is KtExpression -> when (val receiverSymbol = expression.mainReference?.resolveToSymbol()) {
                    is KtFunctionSymbol -> {
                        receiverSymbol.isOperator
                                && receiverSymbol.name.asString() == "invoke"
                                && containingClass.classKind.isObject
                                && receiverSymbol.getContainingSymbol() == containingClass
                    }
                    is KtClassOrObjectSymbol -> {
                        receiverSymbol.classKind.isObject
                                && receiverSymbol == containingClass
                    }
                    else -> false
                }
                else -> false
            }
        }

        if (dispatchReceiver is KtImplicitReceiverValue) {
            return dispatchReceiver.symbol == containingClass
        }

        return false
    }

    private class RecursiveCallLineMarkerInfo(
        anchor: PsiElement,
        message: String,
        @NlsSafe private val declarationName: String,
        targetElementPointer: SmartPsiElementPointer<PsiElement>?,
    ) : MergeableLineMarkerInfo<PsiElement>(
        /* element = */ anchor,
        /* textRange = */ anchor.textRange,
        /* icon = */ AllIcons.Gutter.RecursiveMethod,
        /* tooltipProvider = */ { message },
        /* navHandler = */ targetElementPointer?.let(::SimpleNavigationHandler),
        /* alignment = */ GutterIconRenderer.Alignment.RIGHT,
        /* accessibleNameProvider = */ { message }
    ) {
        override fun createGutterRenderer() = LineMarkerGutterIconRenderer(this)
        override fun getElementPresentation(element: PsiElement) = declarationName

        override fun canMergeWith(info: MergeableLineMarkerInfo<*>) = info is RecursiveCallLineMarkerInfo
        override fun getCommonIcon(infos: List<MergeableLineMarkerInfo<*>>) = infos.firstNotNullOf { it.icon }
    }
}