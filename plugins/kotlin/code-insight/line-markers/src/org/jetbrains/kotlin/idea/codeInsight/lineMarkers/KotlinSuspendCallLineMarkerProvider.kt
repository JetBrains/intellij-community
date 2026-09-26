// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.GutterName
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinCallProcessor
import org.jetbrains.kotlin.idea.base.codeInsight.process
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import javax.swing.Icon

private val suspendCallOptions = arrayOf(KotlinLineMarkerOptions.suspendCallOption)

internal class KotlinSuspendCallLineMarkerProvider : AbstractKotlinLineMarkerProvider() {
    override fun getName(): @GutterName String =
        KotlinLineMarkersBundle.message("line.markers.suspend.call.description")

    override fun getOptions(): Array<Option> = suspendCallOptions

    override fun doCollectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
        KotlinCallProcessor.process(elements) { target ->
            val symbol = target.symbol

            if (symbol is KaNamedFunctionSymbol && symbol.isSuspend) {
                val name = symbol.name.asString()
                val isOperator = symbol.isOperator

                @NlsSafe val declarationName = "$name()"

                val message = when {
                    isOperator -> KotlinLineMarkersBundle.message("line.markers.suspend.operator.call.description", declarationName)
                    else -> KotlinLineMarkersBundle.message("line.markers.suspend.function.call.description", declarationName)
                }

                result += SuspendCallLineMarkerInfo(target.anchorLeaf, message, declarationName, symbol.psi?.createSmartPointer())
            } else if (symbol is KaKotlinPropertySymbol && symbol.callableId == COROUTINE_CONTEXT_CALLABLE_ID) {
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
        override fun createGutterRenderer(): LineMarkerGutterIconRenderer<PsiElement?> = LineMarkerGutterIconRenderer(this)
        override fun getElementPresentation(element: PsiElement): String = declarationName

        override fun canMergeWith(info: MergeableLineMarkerInfo<*>): Boolean = info is SuspendCallLineMarkerInfo
        override fun getCommonIcon(infos: List<MergeableLineMarkerInfo<*>>): Icon = infos.firstNotNullOf { it.icon }
    }
}

private val COROUTINE_CONTEXT_CALLABLE_ID = CallableId(FqName("kotlin.coroutines"), Name.identifier("coroutineContext"))
