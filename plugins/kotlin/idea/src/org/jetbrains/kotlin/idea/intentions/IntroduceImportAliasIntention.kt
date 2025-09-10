// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.FileModificationService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.imports.canBeAddedToImport
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceImportAlias.KotlinIntroduceImportAliasHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class IntroduceImportAliasIntention : SelfTargetingRangeIntention<KtNameReferenceExpression>(
    KtNameReferenceExpression::class.java,
    KotlinBundle.messagePointer("introduce.import.alias")
) {
    override fun applicabilityRange(element: KtNameReferenceExpression): TextRange? {
        if (element.parent is KtInstanceExpressionWithLabel || element.mainReference.getImportAlias() != null) return null

        val targets = element.resolveMainReferenceToDescriptors()
        if (targets.isEmpty() || targets.any { !it.canBeAddedToImport() }) return null
        // It is a workaround: KTIJ-20142 actual FE could not report ambiguous references for alias for a broken reference
        if (element.mainReference.resolve() == null) return null
        return element.textRange
    }

    override fun startInWriteAction(): Boolean = false

    override fun applyTo(element: KtNameReferenceExpression, editor: Editor?) {
        if (editor == null || !FileModificationService.getInstance().preparePsiElementsForWrite(element)) return
        val project = element.project
        KotlinIntroduceImportAliasHandler.doRefactoring(project, editor, element)
    }
}