// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyseInModalWindow
import org.jetbrains.kotlin.analysis.api.analyseWithReadAction
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.util.showYesNoCancelDialog
import org.jetbrains.kotlin.idea.refactoring.CHECK_SUPER_METHODS_YES_NO_DIALOG
import org.jetbrains.kotlin.idea.refactoring.formatPsiClass
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class KotlinFindUsagesSupportFirImpl : KotlinFindUsagesSupport {
    override fun processCompanionObjectInternalReferences(
        companionObject: KtObjectDeclaration,
        referenceProcessor: Processor<PsiReference>
    ): Boolean {
        val klass = companionObject.getStrictParentOfType<KtClass>() ?: return true
        return !klass.anyDescendantOfType(fun(element: KtElement): Boolean {
            if (element == companionObject) return false
            var result = false
            forResolvedCall(element) { call ->
                if (callReceiverRefersToCompanionObject(call, companionObject)) {
                    result = element.references.any {
                        // We get both a simple named reference and an invoke function
                        // reference for all function calls. We want the named reference.
                        //
                        // TODO: with FE1.0 the check for reference type is not needed.
                        // With FE1.0 two references that point to the same PSI are
                        // obtained and one is filtered out by the reference processor.
                        // We should make FIR references behave the same.
                        it !is KtInvokeFunctionReference && !referenceProcessor.process(it)
                    }
                }
            }
            return result
        })
    }

    private fun forResolvedCall(element: KtElement, block: KtAnalysisSession.(KtCall) -> Unit) {
        analyseWithReadAction(element) {
            element.resolveCall()?.calls?.singleOrNull()?.let { block(it) }
        }
    }

    private fun KtAnalysisSession.callReceiverRefersToCompanionObject(call: KtCall, companionObject: KtObjectDeclaration): Boolean {
        if (call !is KtCallableMemberCall<*, *>) return false
        val dispatchReceiver = call.partiallyAppliedSymbol.dispatchReceiver
        val extensionReceiver = call.partiallyAppliedSymbol.extensionReceiver
        val companionObjectSymbol = companionObject.getSymbol()
        return (dispatchReceiver as? KtImplicitReceiverValue)?.symbol == companionObjectSymbol ||
                    (extensionReceiver as? KtImplicitReceiverValue)?.symbol == companionObjectSymbol
    }

    override fun isDataClassComponentFunction(element: KtParameter): Boolean {
        // TODO: implement this
        return false
    }

    override fun getTopMostOverriddenElementsToHighlight(target: PsiElement): List<PsiElement> {
        // TODO: implement this
        return emptyList()
    }

    override fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String? {
        // TODO: implement this
        return (declaration as? KtNamedDeclaration)?.name ?: "SUPPORT FOR FIR"
    }

    override fun isConstructorUsage(psiReference: PsiReference, ktClassOrObject: KtClassOrObject): Boolean {
        // TODO: implement this
        return false
    }

    override fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?): List<PsiElement> {
        // TODO: implement this
        return emptyList()
    }

    private fun checkSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?, actionString: String): List<PsiElement> {

        if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return listOf(declaration)

        data class AnalyzedModel(
            val declaredClassRender: String,
            val overriddenDeclarationsAndRenders: Map<PsiElement, String>
        )

        fun getClassDescription(overriddenElement: PsiElement, containingSymbol: KtSymbolWithKind?): String =
            when (overriddenElement) {
                is KtNamedFunction, is KtProperty, is KtParameter -> (containingSymbol as? KtNamedSymbol)?.name?.asString() ?: "Unknown"  //TODO render symbols
                is PsiMethod -> {
                    val psiClass = overriddenElement.containingClass ?: error("Invalid element: ${overriddenElement.text}")
                    formatPsiClass(psiClass, markAsJava = true, inCode = false)
                }
                else -> error("Unexpected element: ${overriddenElement.getElementTextWithContext()}")
            }.let { "    $it\n" }


        val analyzeResult = analyseInModalWindow(declaration, KotlinBundle.message("find.usages.progress.text.declaration.superMethods")) {
            (declaration.getSymbol() as? KtCallableSymbol)?.let { callableSymbol ->
                callableSymbol.originalContainingClassForOverride?.let { containingClass ->
                    val overriddenSymbols = callableSymbol.getAllOverriddenSymbols()

                    val renderToPsi = overriddenSymbols.mapNotNull {
                        it.psi?.let { psi ->
                            psi to getClassDescription(psi, it.originalContainingClassForOverride)
                        }
                    }

                    val filteredDeclarations =
                        if (ignore != null) renderToPsi.filter { ignore.contains(it.first) } else renderToPsi

                    val renderedClass = containingClass.name?.asString() ?: SpecialNames.ANONYMOUS_STRING //TODO render class

                    AnalyzedModel(renderedClass, filteredDeclarations.toMap())
                }
            }
        } ?: return listOf(declaration)

        if (analyzeResult.overriddenDeclarationsAndRenders.isEmpty()) return listOf(declaration)

        val message = KotlinBundle.message(
            "override.declaration.x.overrides.y.in.class.list",
            analyzeResult.declaredClassRender,
            "\n${analyzeResult.overriddenDeclarationsAndRenders.values.joinToString(separator = "")}",
            actionString
        )

        val exitCode = showYesNoCancelDialog(
            CHECK_SUPER_METHODS_YES_NO_DIALOG,
            declaration.project, message, IdeBundle.message("title.warning"), Messages.getQuestionIcon(), Messages.YES
        )

        return when (exitCode) {
            Messages.YES -> listOf(declaration) + analyzeResult.overriddenDeclarationsAndRenders.keys
            Messages.NO -> listOf(declaration)
            else -> emptyList()
        }
    }

    override fun sourcesAndLibraries(delegate: GlobalSearchScope, project: Project): GlobalSearchScope {
        return delegate
    }
}
