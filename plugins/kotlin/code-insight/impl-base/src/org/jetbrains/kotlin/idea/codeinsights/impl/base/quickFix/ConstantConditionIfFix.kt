package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.getConditionConstantValueIfAny
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.replaceWithBranch
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@ApiStatus.Internal
abstract class ConstantConditionIfFix : KotlinModCommandQuickFix<KtIfExpression>() {
    protected abstract fun applyFix(ifExpression: KtIfExpression, updater: ModPsiUpdater?)
    
    companion object {
        fun collectFixes(
            expression: KtIfExpression,
            constantValue: Boolean? = expression.getConditionConstantValueIfAny(),
        ): List<ConstantConditionIfFix> {
            if (constantValue == null) return emptyList()
            val fixes = mutableListOf<ConstantConditionIfFix>()

            if (expression.branch(constantValue) != null) {
                val keepBraces = expression.isElseIf() && expression.branch(constantValue) is KtBlockExpression
                fixes += SimplifyFix(
                    constantValue,
                    analyze(expression) { expression.isUsedAsExpression },
                    keepBraces
                )
            }

            if (!constantValue && expression.`else` == null) {
                fixes += RemoveFix()
            }

            return fixes
        }

        fun applyFixIfSingle(ifExpression: KtIfExpression, updater: ModPsiUpdater? = null) {
            collectFixes(ifExpression).singleOrNull()?.applyFix(ifExpression, updater)
        }
    }
}

private class SimplifyFix(
    private val conditionValue: Boolean,
    private val isUsedAsExpression: Boolean,
    private val keepBraces: Boolean,
) : ConstantConditionIfFix() {
    override fun getFamilyName(): String = KotlinBundle.message("simplify.fix.text")

    override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
        applyFix(element, updater)
    }
    
    override fun applyFix(ifExpression: KtIfExpression, updater: ModPsiUpdater?) {
        val branch = ifExpression.branch(conditionValue)?.let {
            if (keepBraces) it else it.getSingleUnwrappedStatementOrThis()
        } ?: return
        
        val replacedBranch = ifExpression.replaceWithBranch(branch, isUsedAsExpression, keepBraces)
        
        if (replacedBranch != null) {
            updater?.moveCaretTo(replacedBranch.startOffset)
        }
    }
}

private class RemoveFix : ConstantConditionIfFix() {
    override fun getFamilyName(): String = KotlinBundle.message("remove.fix.text")

    override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
        applyFix(element, updater)
    }

    override fun applyFix(ifExpression: KtIfExpression, updater: ModPsiUpdater?) {
        val parent = ifExpression.parent
        if (parent.node.elementType == KtNodeTypes.ELSE) {
            (parent.parent as? KtIfExpression)?.elseKeyword?.delete()
            parent.delete()
        }

        ifExpression.delete()
    }
}

private fun KtIfExpression.branch(thenBranch: Boolean): KtExpression? = if (thenBranch) then else `else`

private fun KtExpression.isElseIf(): Boolean = parent.node.elementType == KtNodeTypes.ELSE