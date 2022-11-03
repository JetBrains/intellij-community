// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenCommand
import org.jetbrains.kotlin.analysis.api.components.ShortenOption
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.firstExpressionWithoutReceiver
import org.jetbrains.kotlin.idea.codeinsight.utils.isReferenceToBuiltInEnumFunction
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import javax.swing.JComponent

class RemoveRedundantQualifierNameInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    /**
     * In order to detect that `foo()` and `GrandBase.foo()` point to the same method,
     * we need to unwrap fake overrides from descriptors. If we don't do that, they will
     * have different `fqName`s, and the inspection will not detect `GrandBase` as a
     * redundant qualifier.
     */
    var unwrapFakeOverrides: Boolean = false

    override fun createOptionsPanel(): JComponent = SingleCheckboxOptionsPanel(
        KotlinBundle.message("redundant.qualifier.unnecessary.non.direct.parent.class.qualifier"), this, ::unwrapFakeOverrides.name
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            /**
             * The following three lines filter [expression] whose parent is not one of
             * [KtDotQualifiedExpression], package directive, nor import directive.
             *
             * For example, for the following code,
             *
             * package a.b.c
             * import x.y.z
             * ...
             * val foo = x.y.z.bar()
             *
             * It will filter out `a.b.c` in `package a.b.c`, `x.y.z` in `import x.y.z`, and `x.y.z` in `x.y.z.bar()`, while it will not
             * filter `x.y.z.bar()` out. Note that `x.y.z` is [KtDotQualifiedExpression], but its parent `x.y.z.bar()` is also
             * [KtDotQualifiedExpression]. The parent of [KtDotQualifiedExpression] `x.y.z.bar()` is [KtProperty] `val foo = x.y.z.bar()`.
             *
             * `expressionForAnalyze` will be
             *   - `x.y.z` if `x.y.z` is an instance of a class.
             *   - `x.y.z.bar()` if `x.y.z` is an object.
             */
            val expressionParent = expression.parent
            if (expressionParent is KtDotQualifiedExpression || expressionParent is KtPackageDirective || expressionParent is KtImportDirective) return
            var expressionForAnalyze = expression.firstExpressionWithoutReceiver() ?: return

            if (expressionForAnalyze.selectorExpression?.text == expressionParent.getNonStrictParentOfType<KtProperty>()?.name) return

            val receiver = expressionForAnalyze.receiverExpression
            val receiverDeclaration = receiver.getQualifiedElementSelectorDeclaration()
            var hasCompanion = false
            var callingBuiltInEnumFunction = false
            when {
                isEnumCompanionObject(receiverDeclaration) -> when (receiver) {
                    is KtDotQualifiedExpression -> {
                        if (isEnumClass(receiver.receiverExpression.getQualifiedElementSelectorDeclaration())) return
                        expressionForAnalyze = receiver
                    }
                }

                isEnumClass(receiverDeclaration) -> {
                    hasCompanion = expressionForAnalyze.selectorExpression?.getQualifiedElementSelectorDeclaration()
                        ?.let { isEnumCompanionObject(it) } == true
                    callingBuiltInEnumFunction = expressionForAnalyze.isReferenceToBuiltInEnumFunction()
                    when {
                        receiver is KtDotQualifiedExpression -> expressionForAnalyze = receiver
                        hasCompanion || callingBuiltInEnumFunction -> return
                    }
                }
            }

            val shortenings = analyze(expressionForAnalyze) {
                collectPossibleReferenceShortenings(expressionForAnalyze.containingKtFile,
                                                    TextRange(expressionForAnalyze.startOffset, expressionForAnalyze.endOffset),
                                                    { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED },
                                                    { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED })
            }
            if (!shortenings.isEmpty) reportProblem(holder, expressionForAnalyze, shortenings)
        }

        override fun visitUserType(type: KtUserType) {
            if (type.parent is KtUserType) return

            val shortenings = analyze(type) {
                collectPossibleReferenceShortenings(type.containingKtFile,
                                                    TextRange(type.startOffset, type.endOffset),
                                                    { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED },
                                                    { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED })
            }
            if (!shortenings.isEmpty) reportProblem(holder, type, shortenings)
        }
    }
}

private fun KtExpression.getQualifiedElementSelectorDeclaration() = getQualifiedElementSelector()?.mainReference?.resolve()

private fun isEnumClass(element: PsiElement?): Boolean {
    val klass = element as? KtClass ?: return false
    return klass.isEnum()
}

private fun isEnumCompanionObject(element: PsiElement?): Boolean {
    val klass = element?.getNonStrictParentOfType<KtClass>() ?: return false
    if (!klass.isEnum()) return false
    return (element as? KtObjectDeclaration)?.isCompanion() == true
}

private fun reportProblem(holder: ProblemsHolder, element: KtElement, shortenings: ShortenCommand) {
    val firstChild = element.firstChild
    holder.registerProblem(
        element,
        KotlinBundle.message("redundant.qualifier.name"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        TextRange.from(firstChild.startOffsetInParent, firstChild.textLength),
        RemoveRedundantQualifierNameQuickFix(shortenings)
    )
}

class RemoveRedundantQualifierNameQuickFix(private val shortenings: ShortenCommand) : LocalQuickFix {
    override fun getName() = KotlinBundle.message("remove.redundant.qualifier.name.quick.fix.text")
    override fun getFamilyName() = name

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile as KtFile
        val range = when (val element = descriptor.psiElement) {
            is KtUserType -> IntRange(element.startOffset, element.endOffset)
            is KtDotQualifiedExpression -> {
                val selectorReference = element.selectorExpression?.mainReference?.resolve()
                val endOffset = if (isEnumClass(selectorReference) || isEnumCompanionObject(selectorReference)) {
                    element.endOffset
                } else {
                    element.getLastParentOfTypeInRowWithSelf<KtDotQualifiedExpression>()?.getQualifiedElementSelector()?.endOffset ?: return
                }
                IntRange(element.startOffset, endOffset)
            }

            else -> IntRange.EMPTY
        }

        val substring = file.text.substring(range.first, range.last)
        Regex.fromLiteral(substring).findAll(file.text, file.importList?.endOffset ?: 0).toList().asReversed().forEach {
            // Reuse `ShortenCommand` that we already got.
            if (range.first == it.range.first) {
                runWriteAction {
                    shortenings.invokeShortening()
                }
                return@forEach
            }

            val shorteningForOtherExpression = allowAnalysisOnEdt {
                analyze(file) {
                    collectPossibleReferenceShortenings(file,
                                                        TextRange(it.range.first, it.range.last + 1),
                                                        { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED },
                                                        { ShortenOption.SHORTEN_IF_ALREADY_IMPORTED })
                }
            }
            runWriteAction {
                shorteningForOtherExpression.invokeShortening()
            }
        }
    }
}