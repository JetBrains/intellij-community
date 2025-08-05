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
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.codeInsight.CallTarget
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.process
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.parents

internal class KotlinRecursiveCallLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (!KotlinLineMarkerOptions.recursiveOption.isEnabled) return
        KotlinCallProcessor.process(elements) { target ->
            val symbol = target.symbol
            val targetDeclaration = target.symbol.psi as? KtDeclaration ?: return@process

            if (targetDeclaration is KtDestructuringDeclaration) return@process

            if (symbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED || !targetDeclaration.isAncestor(target.caller)) {
                return@process
            }

            if (isRecursiveCall(target, targetDeclaration)) {
                @NlsSafe val declarationName = when (symbol) {
                  is KaVariableSymbol -> symbol.name.asString()
                    is KaNamedFunctionSymbol -> symbol.name.asString() + "()"
                    is KaPropertyGetterSymbol -> "get()"
                    is KaPropertySetterSymbol -> "set()"
                    is KaConstructorSymbol -> "constructor()"
                    else -> return@process
                }

                val message = KotlinLineMarkersBundle.message("line.markers.recursive.call.description")
                result += RecursiveCallLineMarkerInfo(target.anchorLeaf, message, declarationName, targetDeclaration.createSmartPointer())
            }
        }
    }

    context(_: KaSession)
    private fun isRecursiveCall(target: CallTarget, targetDeclaration: PsiElement): Boolean {
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

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
private fun checkDispatchReceiver(target: CallTarget): Boolean {
        var dispatchReceiver = target.partiallyAppliedSymbol.dispatchReceiver ?: return true
        while (dispatchReceiver is KaSmartCastedReceiverValue) {
            dispatchReceiver = dispatchReceiver.original
        }

        val containingClass = target.symbol.containingDeclaration as? KaClassSymbol ?: return true

        if (dispatchReceiver is KaExplicitReceiverValue) {
            if (dispatchReceiver.isSafeNavigation) {
                return false
            }

            return when (val expression = KtPsiUtil.deparenthesize(dispatchReceiver.expression)) {
                is KtThisExpression -> expression.instanceReference.mainReference.resolveToSymbol() == containingClass
                is KtExpression -> when (val receiverSymbol = expression.mainReference?.resolveToSymbol()) {
                    is KaNamedFunctionSymbol -> {
                        receiverSymbol.isOperator
                                && receiverSymbol.name.asString() == "invoke"
                                && containingClass.classKind.isObject
                                && receiverSymbol.containingDeclaration == containingClass
                    }
                    is KaClassSymbol -> {
                        receiverSymbol.classKind.isObject
                                && receiverSymbol == containingClass
                    }
                    else -> false
                }
                else -> false
            }
        }

        if (dispatchReceiver is KaImplicitReceiverValue) {
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