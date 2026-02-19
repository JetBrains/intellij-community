// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens.SHEBANG_COMMENT
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.isExecutable

internal class ScriptShebangExecPermissionInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitKtFile(file: KtFile) {
                if (!file.isScript()) return
                val shebang = file.children.filterIsInstance<PsiComment>().firstOrNull { it.tokenType === SHEBANG_COMMENT } ?: return
                if (Path(file.virtualFilePath).isExecutable()) return

                holder.registerProblem(
                    shebang,
                    KotlinBundle.message("script.not.executable.missing.execute.permission"),
                    ProblemHighlightType.WARNING,
                    MakeExecutable(file.virtualFilePath)
                )
            }
        }
    }

    class MakeExecutable(val virtualFilePath: String) : LocalQuickFix {
        override fun getFamilyName(): @IntentionFamilyName String {
            return KotlinBundle.message("make.script.executable")
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            File(virtualFilePath).setExecutable(true)
        }

        override fun startInWriteAction(): Boolean = false
    }
}
