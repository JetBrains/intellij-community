// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtKotlinPropertySymbol
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import java.awt.event.MouseEvent

class KotlinSuspendCallLineMarkerProvider : LineMarkerProvider {
    private companion object {
        private val NAME_REFERENCE_IGNORED_PARENTS = arrayOf(
            KtUserType::class.java,
            KtImportDirective::class.java,
            KtPackageDirective::class.java,
            KtValueArgumentName::class.java,
            PsiComment::class.java,
            KDoc::class.java
        )

        private val COROUTINE_CONTEXT_CALLABLE_ID = CallableId(FqName("kotlin.coroutines"), Name.identifier("coroutineContext"))
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            ProgressManager.checkCanceled()

            val containingFile = element.containingFile
            if (containingFile is KtCodeFragment) {
                continue
            }

            when (element) {
                is KtArrayAccessExpression -> collectMarkersForElement(element, result)
                is KtCallExpression -> collectMarkersForElement(element, result)
                is KtOperationReferenceExpression -> collectMarkersForElement(element, result)
                is KtForExpression -> collectMarkersForElement(element, result)
                is KtNameReferenceExpression -> {
                    if (shouldHandleNameReference(element)) {
                        collectMarkersForElement(element, result)
                    }
                }
            }
        }
    }

    private fun shouldHandleNameReference(element: KtNameReferenceExpression): Boolean {
        val parent = element.parent
        if (parent is KtCallableReferenceExpression && element == parent.callableReference) {
            return false
        }

        return PsiTreeUtil.getParentOfType(element, *NAME_REFERENCE_IGNORED_PARENTS) == null
    }

    private fun collectMarkersForElement(element: KtElement, result: MutableCollection<in LineMarkerInfo<*>>) {
        val reference = element.mainReference ?: return

        analyze(element) {
            val anchor by lazy {
                when (element) {
                    is LeafPsiElement -> element
                    else -> generateSequence<PsiElement>(element) { it.firstChild }.last()
                }
            }

            for (symbol in reference.resolveToSymbols()) {
                if (symbol is KtFunctionSymbol && symbol.isSuspend) {
                    val name = symbol.name.asString()
                    val isOperator = symbol.isOperator
                            || element is KtOperationReferenceExpression
                            || element is KtForExpression

                    @NlsSafe val declarationName = "$name()"

                    val message = when {
                        isOperator -> KotlinLineMarkersBundle.message("line.markers.suspend.operator.call.description", declarationName)
                        else -> KotlinLineMarkersBundle.message("line.markers.suspend.function.call.description", declarationName)
                    }

                    result += SuspendCallLineMarkerInfo(anchor, message, declarationName, symbol.psi?.createSmartPointer())
                } else if (symbol is KtKotlinPropertySymbol && symbol.callableIdIfNonLocal == COROUTINE_CONTEXT_CALLABLE_ID) {
                    val message = KotlinLineMarkersBundle.message("line.markers.coroutine.context.call.description")
                    result += SuspendCallLineMarkerInfo(anchor, message, symbol.name.asString(), symbol.psi?.createSmartPointer())
                }
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
        /* navHandler = */ targetElementPointer?.let(::SymbolNavigationHandler),
        /* alignment = */ GutterIconRenderer.Alignment.RIGHT,
        /* accessibleNameProvider = */ { message }
    ) {
        override fun createGutterRenderer() = LineMarkerGutterIconRenderer(this)
        override fun getElementPresentation(element: PsiElement) = declarationName

        override fun canMergeWith(info: MergeableLineMarkerInfo<*>) = info is SuspendCallLineMarkerInfo
        override fun getCommonIcon(infos: List<MergeableLineMarkerInfo<*>>) = infos.firstNotNullOf { it.icon }
    }

    private class SymbolNavigationHandler(
        private val targetElementPointer: SmartPsiElementPointer<PsiElement>
    ) : GutterIconNavigationHandler<PsiElement> {
        override fun navigate(event: MouseEvent, element: PsiElement) {
            val targetElement = targetElementPointer.element ?: return

            EditSourceUtil.getDescriptor(targetElement)
                ?.takeIf { it.canNavigate() }
                ?.navigate(true)
        }
    }
}