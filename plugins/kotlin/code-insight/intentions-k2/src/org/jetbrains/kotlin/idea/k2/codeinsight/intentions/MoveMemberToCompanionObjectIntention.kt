// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.MoveMemberToCompanionObjectUtils.invokeRenameOnAddedParameter
import org.jetbrains.kotlin.idea.codeinsight.utils.MoveMemberToCompanionObjectUtils.suggestInstanceName
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usesOuterInstanceParameter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class MoveMemberToCompanionObjectIntention : SelfTargetingRangeIntention<KtNamedDeclaration>(
    elementType = KtNamedDeclaration::class.java,
    textGetter = KotlinBundle.lazyMessage("move.to.companion.object")
) {
    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (element !is KtNamedFunction && element !is KtProperty && element !is KtClassOrObject) return null
        if (element is KtEnumEntry) return null
        if (element is KtNamedFunction && element.bodyExpression == null) return null
        if (element is KtNamedFunction && element.valueParameterList == null) return null
        if ((element is KtNamedFunction || element is KtProperty) && element.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return null
        if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null
        val containingClass = element.containingClassOrObject as? KtClass ?: return null
        if (containingClass.isLocal || containingClass.isInner()) return null

        val nameIdentifier = element.nameIdentifier ?: return null
        if (element is KtProperty && element.hasModifier(KtTokens.CONST_KEYWORD) && !element.isVar) {
            val constElement = element.modifierList?.allChildren?.find { it.node.elementType == KtTokens.CONST_KEYWORD }
            if (constElement != null) return TextRange(constElement.startOffset, nameIdentifier.endOffset)
        }
        return nameIdentifier.textRange
    }

    override fun startInWriteAction(): Boolean = false

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        val containingClass = element.containingClassOrObject as? KtClass ?: return

        val moveDescriptor = K2MoveDescriptor.Declarations(
            element.project,
            K2MoveSourceDescriptor.ElementSource(setOf(element)),
            K2MoveTargetDescriptor.CompanionObject(containingClass)
        )

        val instanceName = allowAnalysisOnEdt {
            if (element.usesOuterInstanceParameter()) {
                element.suggestInstanceName()
            } else null
        }

        var movedElement: KtNamedDeclaration? = null
        val processor = K2MoveOperationDescriptor.NestedDeclarations(
            element.project,
            listOf(moveDescriptor),
            searchForText = false,
            searchInComments = false,
            searchReferences = true,
            dirStructureMatchesPkg = false,
            outerInstanceParameterName = instanceName,
            postDeclarationMoved = { old, new ->
                if (old == element) {
                    movedElement = new
                }
            },
            moveCallBack = {
                ApplicationManager.getApplication().invokeLater {
                    if (movedElement?.isValid != true) return@invokeLater
                    movedElement.invokeRenameOnAddedParameter(instanceName, editor)
                }
            }
        ).refactoringProcessor()

        // Need to set this for the conflict dialog to be shown
        processor.setPrepareSuccessfulSwingThreadCallback { }
        processor.run()
    }
}