package org.jetbrains.kotlin.idea.highlighting

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.highlighting.AbstractKotlinHighlightExitPointsHandlerFactory
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument as utilsIsInlinedArgument

class KotlinHighlightExitPointsHandlerFactory: AbstractKotlinHighlightExitPointsHandlerFactory() {

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun getRelevantReturnDeclaration(returnExpression: KtReturnExpression): KtDeclarationWithBody? {
        val psi = allowAnalysisOnEdt {
            analyze(returnExpression) {
                returnExpression.getReturnTargetSymbol()?.psi
            }
        }
        return psi as? KtDeclarationWithBody
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isInlinedArgument(declaration: KtDeclarationWithBody): Boolean {
        return declaration is KtFunction && allowAnalysisOnEdt {
            analyze(declaration) {
                utilsIsInlinedArgument(declaration)
            }
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun hasNonUnitReturnType(functionLiteral: KtFunctionLiteral): Boolean =
        allowAnalysisOnEdt {
            analyze(functionLiteral) {
                val returnType = functionLiteral.getAnonymousFunctionSymbol().returnType
                !(returnType.isUnit || returnType.isNothing)
            }
        }
}