package org.jetbrains.kotlin.idea.highlighting

import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.highlighting.AbstractKotlinHighlightExitPointsHandlerFactory
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument as utilsIsInlinedArgument

class KotlinHighlightExitPointsHandlerFactory: AbstractKotlinHighlightExitPointsHandlerFactory() {

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun getRelevantReturnDeclaration(returnExpression: KtReturnExpression): KtDeclarationWithBody? {
        val psi = allowAnalysisOnEdt {
            analyze(returnExpression) {
                returnExpression.targetSymbol?.psi
            }
        }
        return psi as? KtDeclarationWithBody
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun isInlinedArgument(declaration: KtDeclarationWithBody): Boolean {
        return declaration is KtFunction && allowAnalysisOnEdt {
            analyze(declaration) {
                utilsIsInlinedArgument(declaration)
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun hasNonUnitReturnType(functionLiteral: KtFunctionLiteral): Boolean =
        allowAnalysisOnEdt {
            analyze(functionLiteral) {
                val returnType = functionLiteral.symbol.returnType
                !(returnType.isUnitType || returnType.isNothingType)
            }
        }
}