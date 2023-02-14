// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtKotlinPropertySymbol
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.process
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class KotlinSuspendCallLineMarkerProvider : LineMarkerProvider {
    private companion object {
        private val COROUTINE_CONTEXT_CALLABLE_ID = CallableId(FqName("kotlin.coroutines"), Name.identifier("coroutineContext"))
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        KotlinCallProcessor.process(elements) { target ->
            val symbol = target.symbol

            if (symbol is KtFunctionSymbol && symbol.isSuspend) {
                val name = symbol.name.asString()
                val isOperator = symbol.isOperator

                @NlsSafe val declarationName = "$name()"

                val message = when {
                    isOperator -> KotlinLineMarkersBundle.message("line.markers.suspend.operator.call.description", declarationName)
                    else -> KotlinLineMarkersBundle.message("line.markers.suspend.function.call.description", declarationName)
                }

                result += SuspendCallLineMarkerInfo(target.anchorLeaf, message, declarationName, symbol.psi?.createSmartPointer())
            } else if (symbol is KtKotlinPropertySymbol && symbol.callableIdIfNonLocal == COROUTINE_CONTEXT_CALLABLE_ID) {
                val message = KotlinLineMarkersBundle.message("line.markers.coroutine.context.call.description")
                result += SuspendCallLineMarkerInfo(target.anchorLeaf, message, symbol.name.asString(), symbol.psi?.createSmartPointer())
            }
        }
    }

    private class SuspendCallLineMarkerInfo(
        anchor: PsiElement,
        message: String,
        @NlsSafe private val declarationName: String,
        targetElementPointer: SmartPsiElementPointer<PsiElement>?,
    ) : MergeableLineMarkerInfo<PsiElement>(
        /* element = */ anchor,
        /* textRange = */ anchor.textRange,
        /* icon = */ KotlinIcons.SUSPEND_CALL,
        /* tooltipProvider = */ { message },
        /* navHandler = */ targetElementPointer?.let(::SimpleNavigationHandler),
        /* alignment = */ GutterIconRenderer.Alignment.RIGHT,
        /* accessibleNameProvider = */ { message }
    ) {
        override fun createGutterRenderer() = LineMarkerGutterIconRenderer(this)
        override fun getElementPresentation(element: PsiElement) = declarationName

        override fun canMergeWith(info: MergeableLineMarkerInfo<*>) = info is SuspendCallLineMarkerInfo
        override fun getCommonIcon(infos: List<MergeableLineMarkerInfo<*>>) = infos.firstNotNullOf { it.icon }
    }
}