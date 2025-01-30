// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.idea.quickfix.convertMemberToExtensionAndPrepareBodySelection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal object DeclarationCantBeInlinedFixFactories {

    val fixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.DeclarationCantBeInlined ->
        val function = diagnostic.psi as? KtNamedFunction ?: return@IntentionBased emptyList()
        val containingClass = function.containingClass() ?: return@IntentionBased emptyList()

        when {
            containingClass.isInterface() -> listOf(ConvertMemberToExtensionFix(function))
            function.hasModifier(KtTokens.OPEN_KEYWORD) -> listOf(RemoveModifierFixBase(function, KtTokens.OPEN_KEYWORD, false))
            else -> emptyList()
        }
    }
}

private class ConvertMemberToExtensionFix(
    element: KtNamedFunction,
) : KotlinQuickFixAction<KtNamedFunction>(element) {
    override fun getText(): @IntentionName String = KotlinBundle.message("convert.member.to.extension")
    override fun getFamilyName() = text
    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.also {
            convertMemberToExtensionAndPrepareBodySelection(it, true, KtFile::addImport)
        }
    }
}
