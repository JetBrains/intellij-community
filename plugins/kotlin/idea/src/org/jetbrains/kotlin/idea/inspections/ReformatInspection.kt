// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.*
import com.intellij.codeInspection.incorrectFormatting.IncorrectFormattingInspection
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.psi.KtFile


class ReformatInspection(@JvmField var processChangedFilesOnly: Boolean = false) : LocalInspectionTool() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {

        if (file !is KtFile || !file.isWritable || !ProjectRootsUtil.isInProjectSource(file)) {
            return null
        }

        if (isIncorrectFormattingInspectionEnabled(file.project)) {
            return null
        }

        return arrayOf(
            manager.createProblemDescriptor(
                file,
                KotlinBundle.message("kotlin.formatting.inspection.is.deprecated"),
                EnableCommonInspection,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly
            )
        )

    }

}


private val incorrectFormattingInspectionKey: HighlightDisplayKey? by lazy { HighlightDisplayKey.findById(IncorrectFormattingInspection().id) }
private val incorrectFormattingInspectionShortName: String by lazy { IncorrectFormattingInspection().shortName }

private val reformatInspectionShortName: String by lazy { org.jetbrains.kotlin.idea.inspections.ReformatInspection().shortName }

private fun isIncorrectFormattingInspectionEnabled(project: Project) =
    InspectionProjectProfileManager
        .getInstance(project)
        .currentProfile
        .isToolEnabled(incorrectFormattingInspectionKey)

private object EnableCommonInspection : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("enable.reformat.inspection.fix.family.name")
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        InspectionProjectProfileManager
            .getInstance(project)
            .currentProfile
            .enableTool(incorrectFormattingInspectionShortName, project)

        InspectionProjectProfileManager
            .getInstance(project)
            .currentProfile
            .setToolEnabled(reformatInspectionShortName, false, project)
    }
}
