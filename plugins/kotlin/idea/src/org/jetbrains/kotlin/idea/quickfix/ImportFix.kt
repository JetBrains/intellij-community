// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal open class ImportFix(expression: KtSimpleNameExpression) : AbstractImportFix(expression, MyFactory) {

    companion object MyFactory : Factory() {
        private val lazyImportSuggestionCalculation = Registry.`is`("kotlin.lazy.import.suggestions", false)

        override fun createImportAction(diagnostic: Diagnostic): ImportFix? =
            diagnostic.psiElement.safeAs<KtSimpleNameExpression>()?.let {
                val hintsEnabled = !lazyImportSuggestionCalculation || ShowAutoImportPass.isAddUnambiguousImportsOnTheFlyEnabled(diagnostic.psiFile)
                if (hintsEnabled) it.let(::ImportFixWithHint) else it.let(::ImportFix)
            }
    }
}

internal class ImportFixWithHint(expression: KtSimpleNameExpression): ImportFix(expression), HintAction {
    override fun fixSilently(editor: Editor): Boolean {
        if (isOutdated()) return false
        val element = element ?: return false
        val project = element.project
        val addImportAction = createActionWithAutoImportsFilter(project, editor, element)
        return if (addImportAction.isUnambiguous()) {
            addImportAction.execute()
            true
        } else false
    }
}
