// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.help.HelpManager
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSettings
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtWhenExpression
import kotlin.reflect.KMutableProperty1

class KotlinInlinePropertyDialog(
    property: KtProperty,
    reference: PsiReference?,
    private val assignmentToDelete: KtBinaryExpression?,
    withPreview: Boolean = true,
    editor: Editor?,
) : AbstractKotlinInlineDialog<KtProperty>(property, reference, editor) {
    private val simpleLocal = declaration.isLocal && (reference == null || occurrencesNumber == 1)

    init {
        setPreviewResults(withPreview && shouldBeShown())
        if (simpleLocal) {
            setDoNotAskOption(object : com.intellij.openapi.ui.DoNotAskOption {
                override fun isToBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

                override fun setToBeShown(value: Boolean, exitCode: Int) {
                    EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog = value
                }

                override fun canBeHidden() = true

                override fun shouldSaveOptionsOnCancel() = false

                override fun getDoNotShowMessage() = KotlinBundle.message("message.do.not.show.for.local.variables.in.future")
            })
        }
        init()
    }

    fun shouldBeShown() = !simpleLocal || reference != null && EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog
    override fun doHelpAction() = HelpManager.getInstance().invokeHelp(HelpID.INLINE_VARIABLE)
    override val inlineThisOption: KMutableProperty1<KotlinRefactoringSettings, Boolean> get() = KotlinRefactoringSettings::INLINE_LOCAL_THIS
    override val inlineKeepOption: KMutableProperty1<KotlinRefactoringSettings, Boolean> get() = KotlinRefactoringSettings::INLINE_PROPERTY_KEEP
    override fun createProcessor(): BaseRefactoringProcessor = KotlinInlinePropertyProcessor(
        declaration = declaration,
        reference = reference,
        inlineThisOnly = isInlineThisOnly,
        deleteAfter = !isInlineThisOnly && !(isKeepTheDeclaration && reference != null),
        isWhenSubjectVariable = (declaration.parent as? KtWhenExpression)?.subjectVariable == declaration,
        editor = editor,
        statementToDelete = assignmentToDelete,
        project = project,
    )
}
