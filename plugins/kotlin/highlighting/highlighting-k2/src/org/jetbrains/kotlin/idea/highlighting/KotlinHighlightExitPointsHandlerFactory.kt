package org.jetbrains.kotlin.idea.highlighting

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.resolveSymbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument as utilsIsInlinedArgument

class KotlinHighlightExitPointsHandlerFactory: AbstractKotlinHighlightExitPointsHandlerFactory() {

    @OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
    override fun getRelevantReturnDeclaration(returnExpression: KtReturnExpression): KtDeclarationWithBody? {
        val psi = allowAnalysisOnEdt {
            analyze(returnExpression) {
                returnExpression.resolveSymbol()?.psi
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
                !(returnType.classId == KaStandardTypeClassIds.UNIT || returnType.classId == KaStandardTypeClassIds.NOTHING)
            }
        }
}