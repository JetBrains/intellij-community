// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.*
import com.intellij.internal.statistic.ReportingClassSubstitutor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

open class QuickFixWithDelegateFactory(
    delegateFactory: () -> IntentionAction?
) : IntentionAction, ReportingClassSubstitutor {
    @Nls
    private val familyName: String
    @Nls
    private val text: String
    private val startInWriteAction: Boolean
    private val delegate: IntentionAction?

    init {
        delegate = delegateFactory()
        familyName = delegate?.familyName ?: ""
        text = delegate?.text ?: ""
        startInWriteAction = delegate != null && delegate.startInWriteAction()
    }

    override fun getSubstitutedClass(): Class<*> = delegate?.javaClass ?: javaClass

    override fun getFamilyName() = familyName

    override fun getText() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        return delegate?.isAvailable(project, editor, file) ?: false
    }

    override fun startInWriteAction() = startInWriteAction

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return
        }

        val action = delegate ?: return

        assert(action.detectPriority() == this.detectPriority()) {
            "Incorrect priority of QuickFixWithDelegateFactory wrapper for ${action::class.java.name}"
        }

        action.invoke(project, editor, file)
    }
}

class LowPriorityQuickFixWithDelegateFactory(
    delegateFactory: () -> IntentionAction?
) : QuickFixWithDelegateFactory(delegateFactory), LowPriorityAction

class HighPriorityQuickFixWithDelegateFactory(
    delegateFactory: () -> IntentionAction?
) : QuickFixWithDelegateFactory(delegateFactory), HighPriorityAction

enum class IntentionActionPriority {
    LOW, NORMAL, HIGH
}

fun IntentionAction.detectPriority(): IntentionActionPriority {
    return when (this) {
        is LowPriorityAction -> IntentionActionPriority.LOW
        is HighPriorityAction -> IntentionActionPriority.HIGH
        else -> IntentionActionPriority.NORMAL
    }
}

fun QuickFixWithDelegateFactory(priority: IntentionActionPriority, createAction: () -> IntentionAction?): QuickFixWithDelegateFactory {
    return when (priority) {
        IntentionActionPriority.NORMAL -> QuickFixWithDelegateFactory(createAction)
        IntentionActionPriority.HIGH -> HighPriorityQuickFixWithDelegateFactory(createAction)
        IntentionActionPriority.LOW -> LowPriorityQuickFixWithDelegateFactory(createAction)
    }
}