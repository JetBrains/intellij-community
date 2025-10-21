// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionName
import com.intellij.ide.DataManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.rename.RenamerFactory
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.Declaration
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getValueParameters
import java.util.function.Supplier

internal abstract class MoveMemberIntention(textGetter: Supplier<@IntentionName String>) : SelfTargetingRangeIntention<KtNamedDeclaration>(
    elementType = KtNamedDeclaration::class.java,
    textGetter = textGetter
) {
    internal abstract fun getTarget(element: KtNamedDeclaration): Declaration<*>?

    override fun startInWriteAction(): Boolean = false

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        val target = getTarget(element) ?: return

        val moveDescriptor = K2MoveDescriptor.Declarations(
            project = element.project,
            source = K2MoveSourceDescriptor.ElementSource(setOf(element)),
            target = target
        )

        val originalParameterCount = element.getValueParameters().size
        var declarationWithAddedParameters: KtNamedDeclaration? = null
        val descriptor = K2MoveOperationDescriptor.Declarations(
            element.project,
            listOf(moveDescriptor),
            searchForText = false,
            searchInComments = false,
            searchReferences = true,
            dirStructureMatchesPkg = false,
        )
        val processor = object: K2MoveDeclarationsRefactoringProcessor(descriptor) {
            override fun postDeclarationMoved(
                originalDeclaration: KtNamedDeclaration,
                newDeclaration: KtNamedDeclaration
            ) {
                // Check that the parameter was actually added
                if (originalDeclaration == element && originalParameterCount== newDeclaration.getValueParameters().size - 1) {
                    declarationWithAddedParameters = newDeclaration
                }
            }

            override fun openFilesAfterMoving(movedElements: List<KtNamedDeclaration>) {
                if (declarationWithAddedParameters?.isValid != true) return
                declarationWithAddedParameters.invokeRenameOnFirstParameter(editor)
            }
        }

        // Need to set this for the conflict dialog to be shown
        processor.setPrepareSuccessfulSwingThreadCallback { }
        processor.run()
    }

    private fun KtNamedDeclaration.invokeRenameOnFirstParameter(editor: Editor?) {
        if (editor == null || editor.isDisposed) return
        val addedParameter = when (this) {
            is KtClassOrObject -> {
                primaryConstructorParameters.firstOrNull()
            }
            is KtNamedFunction -> {
                valueParameters.firstOrNull()
            }
            else -> null
        } ?: return

        editor.caretModel.moveToOffset(addedParameter.nameIdentifier?.startOffset ?: return)
        val context = DataManager.getInstance().getDataContext(editor.component)

        val renamer = RenamerFactory.EP_NAME.extensionList
            .flatMap { it.createRenamers(context) }
            .firstOrNull()
        if (renamer == null) return
        renamer.performRename()
    }
}