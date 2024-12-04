package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@ApiStatus.Internal
abstract class ConstantConditionIfFix : KotlinModCommandQuickFix<KtIfExpression>() {
    protected abstract fun applyFix(ifExpression: KtIfExpression, updater: ModPsiUpdater?)
    
    companion object {
        fun collectFixes(
            expression: KtIfExpression,
            constantValue: Boolean? = expression.getConditionConstantValueIfAny()
        ): List<ConstantConditionIfFix> {
            return org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.collectFixes(expression, constantValue)
        }

        fun applyFixIfSingle(ifExpression: KtIfExpression, updater: ModPsiUpdater? = null) {
            collectFixes(ifExpression).singleOrNull()?.applyFix(ifExpression, updater)
        }
    }
}

private class SimplifyFix(
    private val conditionValue: Boolean,
    private val isUsedAsExpression: Boolean,
    private val keepBraces: Boolean
) : ConstantConditionIfFix() {
    override fun getFamilyName() = name

    override fun getName() = KotlinBundle.message("simplify.fix.text")

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
    override fun getFamilyName() = name

    override fun getName() = KotlinBundle.message("remove.fix.text")

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

private fun KtIfExpression.getConditionConstantValueIfAny(): Boolean? {
    var expr = condition
    while (expr is KtParenthesizedExpression) {
        expr = expr.expression
    }
    if (expr !is KtConstantExpression) return null
    
    return analyze(expr) { (expr.evaluate() as? KaConstantValue.BooleanValue)?.value }
}

private fun collectFixes(
    expression: KtIfExpression,
    constantValue: Boolean? = expression.getConditionConstantValueIfAny()
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

private fun KtIfExpression.branch(thenBranch: Boolean) = if (thenBranch) then else `else`

private fun KtExpression.replaceWithBranch(branch: KtExpression, isUsedAsExpression: Boolean, keepBraces: Boolean = false): PsiElement? {
    val subjectVariable = (this as? KtWhenExpression)?.subjectVariable?.let(fun(property: KtProperty): KtProperty? {
        if (property.annotationEntries.isNotEmpty()) return property
        val initializer = property.initializer ?: return property
        val references = ReferencesSearch.search(property, LocalSearchScope(this)).toList()
        return when (references.size) {
            0 -> property.takeUnless { initializer.hasNoSideEffects() }
            1 -> {
                if (initializer.hasNoSideEffects()) {
                    references.first().element.replace(initializer)
                    null
                } else
                    property
            }
            else -> property
        }
    })

    val psiFactory = KtPsiFactory(project)

    val parent = this.parent
    val replaced = when {
        branch !is KtBlockExpression -> {
            if (subjectVariable != null) {
                replaced(psiFactory.createExpressionByPattern("run { $0\n$1 }", subjectVariable, branch))
            } else {
                replaced(branch)
            }
        }
        isUsedAsExpression -> {
            if (subjectVariable != null) {
                branch.addAfter(psiFactory.createNewLine(), branch.addBefore(subjectVariable, branch.statements.firstOrNull()))
            }
            replaced(psiFactory.createExpressionByPattern("run $0", branch.text))
        }
        else -> {
            val firstChildSibling = branch.firstChild.nextSibling
            val lastChild = branch.lastChild
            val replaced = if (firstChildSibling != lastChild) {
                if (keepBraces) {
                    parent.addAfter(branch, this)
                } else {
                    if (subjectVariable != null) {
                        branch.addAfter(subjectVariable, branch.lBrace)
                        parent.addAfter(psiFactory.createExpression("run ${branch.text}"), this)
                    } else {
                        parent.addRangeAfter(firstChildSibling, lastChild.prevSibling, this)
                    }
                }
            } else {
                null
            }
            delete()
            replaced
        }
    }
    
    return replaced
}

private fun KtExpression.hasNoSideEffects(): Boolean = when (this) {
    is KtStringTemplateExpression -> !hasInterpolation()
    is KtConstantExpression -> true
    else -> analyze(this) { evaluate() != null }
}

private fun KtExpression.isElseIf(): Boolean = parent.node.elementType == KtNodeTypes.ELSE