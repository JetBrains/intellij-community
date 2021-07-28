package org.jetbrains.kotlin.idea.fir.codeInsight.handlers

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoSuperAction
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.fir.codeInsight.handlers.superDeclarations.KotlinSuperDeclarationsInfoService
import org.jetbrains.kotlin.psi.*

class HLGotoSuperActionHandler : CodeInsightActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is KtFile) return
        FeatureUsageTracker.getInstance().triggerFeatureUsed(GotoSuperAction.FEATURE_ID)
        val info = KotlinSuperDeclarationsInfoService.getForDeclarationAtCaret(file, editor) ?: return
        KotlinSuperDeclarationsInfoService.navigateToSuperDeclaration(info, editor)
    }

    override fun startInWriteAction() = false
}
