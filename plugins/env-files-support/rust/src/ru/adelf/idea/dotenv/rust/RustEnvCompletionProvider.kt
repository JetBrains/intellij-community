package ru.adelf.idea.dotenv.rust

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import ru.adelf.idea.dotenv.DotEnvSettings
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi
import ru.adelf.idea.dotenv.common.BaseEnvCompletionProvider

internal class RustEnvCompletionProvider : BaseEnvCompletionProvider(), GotoDeclarationHandler {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val psiElement = parameters.originalPosition ?: return
                if (!DotEnvSettings.getInstance().completionEnabled) return
                if (RustPsiHelper.findWrappingEnvLiteral(psiElement) == null) return

                fillCompletionResultSet(result, psiElement.project)
            }
        })
    }

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement> {
        if (sourceElement == null) return PsiElement.EMPTY_ARRAY

        val stringLiteral = RustPsiHelper.findWrappingEnvLiteral(sourceElement) ?: return PsiElement.EMPTY_ARRAY
        val key = RustPsiHelper.getStringValue(stringLiteral) ?: return PsiElement.EMPTY_ARRAY
        return EnvironmentVariablesApi.getKeyDeclarations(sourceElement.project, key)
    }

    override fun getActionText(context: DataContext): String? = null
}
