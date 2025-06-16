package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.*
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.prevLeafs
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.isUnaryOperatorOnIntLiteralReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

internal class UnusedUnaryOperatorInspection : KotlinApplicableInspectionBase<KtExpression, UnusedUnaryOperatorInspection.Context>() {

    internal class Context(val addMoveUnaryOperatorFix: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitPrefixExpression(expression: KtPrefixExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        val prefix = element.getPrefix() ?: return false
        if (prefix.baseExpression == null) return false
        val operationToken = prefix.operationToken ?: return false
        if (!operationToken.isPlus() && !operationToken.isMinus()) return false

        // Hack to fix KTIJ-196 (unstable `USED_AS_EXPRESSION` marker for KtAnnotationEntry).
        // Though the bug is fixed, removing this line works good for K2 but some tests fail in K1. So leaving this for stable K1 work.
        if (prefix.isInAnnotationEntry) return false

        return true
    }

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        val prefix = element.getPrefix() ?: return null
        val parentBinary = element as? KtBinaryExpression
        if (isUsedAsExpression(prefix, parentBinary)) return null

        val operationReference = prefix.operationReference
        val referencedSymbol = operationReference.resolveToCall()?.successfulFunctionCallOrNull()?.symbol

        val isUnaryOperatorCallOnPrimitiveType = referencedSymbol?.isUnderKotlinPackage()
                                                 ?: isUnaryOperatorOnIntLiteralReference(operationReference.mainReference)

        if (!isUnaryOperatorCallOnPrimitiveType) return null

        val addMoveUnaryOperatorFix = isAddMoveUnaryOperatorFixNeeded(element, prefix)
        return Context(addMoveUnaryOperatorFix)
    }

    private fun KaSession.isAddMoveUnaryOperatorFixNeeded(element: KtExpression, prefix: KtPrefixExpression): Boolean {
        val prevLeaf = element.getPrevLeafIgnoringWhitespaceAndComments() ?: return false
        val prevOperandExpression = prevLeaf.getStrictParentOfType<KtExpression>() ?: return false
        val prevOperandType = prevOperandExpression.expressionType ?: return false

        val currentOperandType = element.expressionType

        if (currentOperandType == null) {
            return prevOperandType.isPrimitive && isUnaryOperatorOnIntLiteralReference(prefix.operationReference.mainReference)
        }

        return currentOperandType.isPrimitive && prevOperandType.isPrimitive ||
               currentOperandType.semanticallyEquals(prevOperandType)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val fixes = mutableListOf<LocalQuickFix>()
        val prefix = element.getPrefix()
        if (prefix != null) {
            val removeFix = LocalQuickFix.from(RemoveUnaryOperatorFix(prefix))
            fixes.add(removeFix!!)
            if (context.addMoveUnaryOperatorFix) {
                val moveFix = LocalQuickFix.from(MoveUnaryOperatorToPreviousLineFix(prefix))
                fixes.add(moveFix!!)
            }
        }

        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("unused.unary.operator"),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ false,
            /* ...fixes = */ *fixes.toTypedArray(),
        )
    }

    override fun getApplicableRanges(element: KtExpression): List<TextRange> = ApplicabilityRange.single(element) { it.getPrefix() }

    context(KaSession)
    private fun isUsedAsExpression(prefix: KtPrefixExpression, parentBinary: KtBinaryExpression?): Boolean {
        if (prefix.operationToken.isPlus()) {
            // consider the unary plus operator unused in cases like `x -+ 1`
            val prev = prefix.getPrevSiblingIgnoringWhitespaceAndComments()
            if (prev is KtOperationReferenceExpression && prev.parent is KtBinaryExpression) return false
        }
        return (parentBinary ?: prefix).isUsedAsExpression
    }
}

private class RemoveUnaryOperatorFix(element: KtPrefixExpression) : PsiUpdateModCommandAction<KtPrefixExpression>(element) {
    override fun getFamilyName(): String = KotlinBundle.message("remove.unary.operator.fix.text")

    override fun invoke(
        context: ActionContext,
        prefixExpression: KtPrefixExpression,
        updater: ModPsiUpdater,
    ) {
        val baseExpression = prefixExpression.baseExpression ?: return
        prefixExpression.replace(baseExpression)
    }
}

private class MoveUnaryOperatorToPreviousLineFix(element: KtPrefixExpression) : PsiUpdateModCommandAction<KtPrefixExpression>(element) {
    override fun getFamilyName(): String = KotlinBundle.message("move.unary.operator.to.previous.line.fix.text")

    override fun invoke(
        context: ActionContext,
        prefixExpression: KtPrefixExpression,
        updater: ModPsiUpdater,
    ) {
        val baseExpression = prefixExpression.baseExpression ?: return
        val prevLeafStartOffset = prefixExpression.getPrevLeafIgnoringWhitespaceAndComments()?.startOffset ?: return
        val prefixEndOffset = prefixExpression.endOffset

        val file = prefixExpression.containingFile
        val document = file.fileDocument
        val project = prefixExpression.project
        val documentManager = PsiDocumentManager.getInstance(project)

        prefixExpression.replace(baseExpression)

        // The reason why the document is used here instead of a direct PSI manipulation is because "moving" the sign from
        // the prefixed expression to the previous line is quite cumbersome
        documentManager.doPostponedOperationsAndUnblockDocument(document)

        document.insertString(prevLeafStartOffset + 1, " ${prefixExpression.operationReference.text}")
        documentManager.commitDocument(document)

        CodeStyleManager.getInstance(project).adjustLineIndent(file, TextRange(prevLeafStartOffset, prefixEndOffset))
    }

    override fun getPresentation(context: ActionContext, element: KtPrefixExpression): Presentation? {
        val actionName = KotlinBundle.message("move.unary.operator.to.previous.line.fix.text")
        return Presentation.of(actionName).withPriority(PriorityAction.Priority.HIGH)
    }
}

private fun KtExpression.getPrevLeafIgnoringWhitespaceAndComments(): PsiElement? =
    prevLeafs.firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }

private fun KtExpression.getPrefix(): KtPrefixExpression? {
    return when (this) {
        is KtPrefixExpression -> this
        is KtBinaryExpression -> {
            if (this.parent is KtBinaryExpression) return null
            var left = this.left
            while (left is KtBinaryExpression) {
                left = left.left
            }
            left as? KtPrefixExpression
        }
        else -> null
    }
}

/**
 * Analogue for K1 `org.jetbrains.kotlin.builtins.KotlinBuiltIns#isUnderKotlinPackage`.
 */
context(KaSession)
private fun KaSymbol.isUnderKotlinPackage(): Boolean {
    return generateSequence(this) { it.containingDeclaration }
        .any { declaration ->
            declaration.importableFqNameStartsWithKotlin()
        }
}

context(KaSession)
private fun KaSymbol.importableFqNameStartsWithKotlin(): Boolean {
    return this.importableFqName?.startsWith(Name.identifier("kotlin")) == true
}

private val KtPrefixExpression.isInAnnotationEntry: Boolean
    get() = parentsWithSelf.takeWhile { it is KtExpression }.last().parent?.parent?.parent is KtAnnotationEntry

private fun IElementType.isPlus(): Boolean = this == KtTokens.PLUS

private fun IElementType.isMinus(): Boolean = this == KtTokens.MINUS