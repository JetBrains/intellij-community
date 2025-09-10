// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.k2.refactoring.introduceImportAlias.KotlinIntroduceImportAliasHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class IntroduceImportAliasIntention : SelfTargetingRangeIntention<KtNameReferenceExpression>(
    KtNameReferenceExpression::class.java,
    KotlinBundle.messagePointer("introduce.import.alias")
), LowPriorityAction {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun applicabilityRange(element: KtNameReferenceExpression): TextRange? {
        if (element.parent is KtInstanceExpressionWithLabel || element.mainReference.getImportAlias() != null) return null

        val declaration = element.mainReference.resolve() as? KtNamedDeclaration ?: return null
        val nonImportableFQN = allowAnalysisOnEdt {
            analyze(declaration) {
                declaration.symbol.importableFqName == null
            }
        }
        if (nonImportableFQN) return null

        return element.textRange
    }

    override fun startInWriteAction(): Boolean = false

    override fun applyTo(element: KtNameReferenceExpression, editor: Editor?) {
        if (editor == null || !FileModificationService.getInstance().preparePsiElementsForWrite(element)) return
        KotlinIntroduceImportAliasHandler.doRefactoring(element.project, editor, element)
    }
}