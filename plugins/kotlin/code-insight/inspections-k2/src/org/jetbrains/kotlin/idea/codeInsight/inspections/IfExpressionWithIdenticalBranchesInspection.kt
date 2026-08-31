// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.expressions.isUsedAsExpression
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.replaceWithBranch
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.findRelevantLoopForExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isSideEffectFreeCondition
import org.jetbrains.kotlin.idea.codeinsight.utils.qualifiedCalleeExpressionTextRange
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.isSemanticMatch
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStartOffsetIn

internal class IfExpressionWithIdenticalBranchesInspection :
    KotlinApplicableInspectionBase<KtIfExpression, IfExpressionWithIdenticalBranchesInspection.Context>() {

    data class Context(
        val wrapBranchInRun: Boolean,
        val mayChangeSemantics: Boolean,
        val branchCommentState: BranchCommentState,
        val isFixAvailable: Boolean
    )

    enum class BranchCommentState {
        NONE_OR_ONE_SIDED,
        MATCHING,
        DIFFERENT
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> {
        val (thenBranch, elseBranch) = element.normalizedBranches() ?: return emptyList()
        return listOf(
            thenBranch.firstLineRangeIn(element),
            elseBranch.firstLineRangeIn(element)
        )
    }

    override fun isApplicableByPsi(element: KtIfExpression): Boolean = element.normalizedBranches() != null

    context(session: KaSession)
    override fun prepareContext(element: KtIfExpression): Context? {
        val condition = element.condition ?: return null
        val mayChangeSemantics = !condition.isSideEffectFreeCondition()

        val (thenBranch, elseBranch) = element.normalizedBranches() ?: return null
        if (!thenBranch.isTextuallyEquivalentTo(elseBranch)) return null

        val isUsedAsExpression = element.isUsedAsExpression
        val thenExpression = element.then ?: return null
        val elseExpression = element.`else` ?: return null

        val branchToKeep = thenExpression.branchToKeep()
        val wrapBranchInRun = branchToKeep is KtBlockExpression && (isUsedAsExpression || branchToKeep.hasDeclarations())
        val hasEscapingControlFlow =
            wrapBranchInRun && (branchToKeep.hasEscapingBreakOrContinue() || branchToKeep.hasReturnToOuterRun())
        if (!thenBranch.isSemanticMatchIncludingThrow(elseBranch)) return null

        val bothBranchesHaveComments = thenExpression.anyDescendantOfType<PsiComment>() && elseExpression.anyDescendantOfType<PsiComment>()
        val branchCommentState = when {
            !bothBranchesHaveComments -> BranchCommentState.NONE_OR_ONE_SIDED
            thenExpression.isTextuallyEquivalentTo(elseExpression, areCommentsSignificant = true) -> BranchCommentState.MATCHING
            else -> BranchCommentState.DIFFERENT
        }
        val isFixAvailable =
            !branchToKeep.isEmptyBlock() && !hasEscapingControlFlow && branchCommentState != BranchCommentState.DIFFERENT

        return Context(wrapBranchInRun, mayChangeSemantics, branchCommentState, isFixAvailable)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtIfExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val fixes = if (context.isFixAvailable) arrayOf(createQuickFix(context)) else emptyArray()

        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("inspection.if.expression.with.identical.branches.problem"),
            /* highlightType = */ GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ *fixes
        )
    }

    private fun createQuickFix(context: Context): KotlinModCommandQuickFix<KtIfExpression> =
        object : KotlinModCommandQuickFix<KtIfExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String =
                KotlinBundle.message("inspection.if.expression.with.identical.branches.fix")

            override fun getName(): @IntentionName String {
                val suffix = if (context.mayChangeSemantics) KotlinBundle.message("quickfix.text.suffix.may.change.semantics") else ""
                return familyName + suffix
            }

            override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
                val branch = element.then?.branchToKeep() ?: return

                if (context.branchCommentState == BranchCommentState.MATCHING) {
                    element.removeDuplicatedComments()
                }

                val commentSaver = CommentSaver(element)
                val parent = element.parent
                val followingSibling = element.nextSibling
                val replacement = element.replaceWithBranchOrRun(project, branch, context.wrapBranchInRun, commentSaver)

                val replacementRange = if (branch is KtBlockExpression && !context.wrapBranchInRun) {
                    PsiChildRange(replacement, followingSibling?.prevSibling ?: parent.lastChild)
                } else {
                    PsiChildRange.singleElement(replacement)
                }
                commentSaver.restore(replacementRange)
                val replacementStartOffset = replacement.startOffset

                if (context.wrapBranchInRun) {
                    val replacementExpression = replacement as KtExpression
                    replacementExpression.qualifiedCalleeExpressionTextRange?.let { calleeRange ->
                        ShortenReferencesFacility.getInstance().shorten(replacementExpression.containingKtFile, calleeRange)
                    }
                }
                updater.moveCaretTo(replacementStartOffset)
            }
        }

    private fun KtIfExpression.replaceWithBranchOrRun(
        project: Project,
        branch: KtExpression,
        wrapBranchInRun: Boolean,
        commentSaver: CommentSaver
    ): PsiElement {
        if (!wrapBranchInRun || branch !is KtBlockExpression) {
            return checkNotNull(replaceWithBranch(branch, isUsedAsExpression = false))
        }

        val branchCopy = branch.copy() as KtBlockExpression
        val runExpression = KtPsiFactory(project).createExpression("${StandardKotlinNames.run.asString()} {}")
        val createdBranch = checkNotNull(PsiTreeUtil.findChildOfType(runExpression, KtBlockExpression::class.java))
        createdBranch.addRangeAfter(branch.firstChild.nextSibling, branch.lastChild.prevSibling, createdBranch.firstChild)

        val replacement = replace(runExpression)
        val replacementBranch = checkNotNull(replacement.findDescendantOfType<KtBlockExpression>())
        commentSaver.markCopiedCommentsAsRestored(replacementBranch, branchCopy)

        return replacement
    }

    private fun KtIfExpression.normalizedBranches(): Pair<KtExpression, KtExpression>? {
        val thenBranch = then?.getSingleUnwrappedStatementOrThis() ?: return null
        val elseBranch = `else`?.getSingleUnwrappedStatementOrThis() ?: return null
        return thenBranch to elseBranch
    }

    /**
     * Returns the single statement without braces unless a declaration must keep its block scope.
     */
    private fun KtExpression.branchToKeep(): KtExpression {
        val unwrapped = getSingleUnwrappedStatementOrThis()
        return if (unwrapped is KtDeclaration) this else unwrapped
    }

    private fun KtExpression.isEmptyBlock(): Boolean = this is KtBlockExpression && statements.isEmpty()

    private fun KtExpression.firstLineRangeIn(element: KtIfExpression): TextRange {
        val firstExpression = (this as? KtBlockExpression)?.statements?.firstOrNull() ?: this
        val range = firstExpression.textRange

        val document = FileDocumentManager.getInstance().getDocument(firstExpression.containingFile.virtualFile)
        val lineEndOffset = document?.getLineEndOffset(document.getLineNumber(range.startOffset)) ?: range.endOffset
        return TextRange(range.startOffset, minOf(range.endOffset, lineEndOffset)).shiftLeft(element.startOffset)
    }

    context(_: KaSession)
    private fun KtExpression.isSemanticMatchIncludingThrow(other: KtExpression): Boolean {
        if (this !is KtThrowExpression || other !is KtThrowExpression) return isSemanticMatch(other)

        val thrownExpression = thrownExpression ?: return false
        val otherThrownExpression = other.thrownExpression ?: return false
        return thrownExpression.isSemanticMatch(otherThrownExpression)
    }

    private fun PsiElement.isTextuallyEquivalentTo(other: PsiElement, areCommentsSignificant: Boolean = false): Boolean =
        PsiEquivalenceUtil.areEquivalent(
            /* element1 = */ this,
            /* element2 = */ other,
            /* refsAreEqual = */ { first, second -> first.element.textMatches(second.element) },
            /* leafsAreEqual = */ null,
            /* isElementSignificantCondition = */ null,
            /* areCommentsSignificant = */ areCommentsSignificant
        )

    private fun KtBlockExpression.hasDeclarations(): Boolean = statements.any { it is KtDeclaration }

    private fun KtIfExpression.removeDuplicatedComments() {
        val elseBranch = `else` ?: return
        PsiTreeUtil.findChildrenOfType(elseBranch, PsiComment::class.java).forEach(PsiElement::delete)
    }

    private fun CommentSaver.markCopiedCommentsAsRestored(createdBranch: KtBlockExpression, branchCopy: KtBlockExpression) {
        val createdComments = PsiTreeUtil.findChildrenOfType(createdBranch, PsiComment::class.java)
        val copiedComments = PsiTreeUtil.findChildrenOfType(branchCopy, PsiComment::class.java)
        check(createdComments.size == copiedComments.size)

        createdComments.zip(copiedComments).forEach { (createdComment, copiedComment) ->
            val rangeInCopy = TextRange.from(copiedComment.getStartOffsetIn(branchCopy), copiedComment.textLength)
            elementCreatedByText(createdComment, branchCopy, rangeInCopy)
        }
    }

    private fun KtBlockExpression.hasEscapingBreakOrContinue(): Boolean =
        anyDescendantOfType<KtExpression> { expression ->
            if (expression !is KtBreakExpression && expression !is KtContinueExpression) return@anyDescendantOfType false
            val targetLoop = findRelevantLoopForExpression(expression) ?: return@anyDescendantOfType true
            !PsiTreeUtil.isAncestor(this, targetLoop, false)
        }

    @OptIn(KaExperimentalApi::class)
    context(_: KaSession)
    private fun KtBlockExpression.hasReturnToOuterRun(): Boolean =
        anyDescendantOfType<KtReturnExpression> { expression ->
            if (expression.getLabelNameAsName() != StandardKotlinNames.run.shortName()) return@anyDescendantOfType false

            val target = expression.resolveSuccessfulSymbol()?.psi ?: return@anyDescendantOfType true
            !PsiTreeUtil.isAncestor(this, target, false)
        }
}
