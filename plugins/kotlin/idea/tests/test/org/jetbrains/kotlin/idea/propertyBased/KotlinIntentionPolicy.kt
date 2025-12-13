// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.propertyBased

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.propertyBased.IntentionPolicy
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.ConvertToScopeIntention
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFixBase
import org.jetbrains.kotlin.idea.refactoring.move.changePackage.ChangePackageIntention

abstract class KotlinIntentionPolicy : IntentionPolicy() {
    override fun shouldSkipIntention(actionText: String): Boolean =
        // These intentions don't modify code (probably should not start in write-action?)
        actionText == "Enable a trailing comma by default in the formatter" ||
        actionText == "Disable a trailing comma by default in the formatter" ||
        // May produce error hint instead of actual action which results in RefactoringErrorHintException
        // TODO: ignore only exceptional case but proceed with normal one
        actionText == "Convert to enum class"

    override fun mayBreakCode(action: IntentionAction, editor: Editor, file: PsiFile): Boolean = false

    override fun shouldTolerateIntroducedError(info: HighlightInfo): Boolean = false
}
