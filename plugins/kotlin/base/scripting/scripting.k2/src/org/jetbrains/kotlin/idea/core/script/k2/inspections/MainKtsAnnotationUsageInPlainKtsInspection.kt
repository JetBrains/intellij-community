// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.FutureVirtualFile
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.modcommand.ModMoveFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.core.script.k2.isMainKtsScript
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val MAIN_KTS_ANNOTATION_FQ_NAMES = setOf(
    FqName("kotlin.script.experimental.dependencies.DependsOn"),
    FqName("kotlin.script.experimental.dependencies.Repository"),
    FqName("org.jetbrains.kotlin.mainKts.Import"),
)
private val MAIN_KTS_ANNOTATION_SHORT_NAMES = MAIN_KTS_ANNOTATION_FQ_NAMES.map { it.shortName().asString() }.toSet()

class MainKtsAnnotationUsageInPlainKtsInspection : KotlinApplicableInspectionBase<KtAnnotationEntry, String>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
            visitTargetElement(annotationEntry, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtAnnotationEntry): Boolean {
        val shortName = element.shortName?.asString() ?: return false
        return shortName in MAIN_KTS_ANNOTATION_SHORT_NAMES && !element.containingKtFile.isMainKtsScript()
    }

    override fun KaSession.prepareContext(element: KtAnnotationEntry): String? {
        val annotationShortName = element.shortName?.asString() ?: return null

        val annotationFqName = element
            .resolveToCall()
            ?.singleConstructorCallOrNull()
            ?.symbol
            ?.containingClassId
            ?.asSingleFqName()

        if (annotationFqName != null && annotationFqName !in MAIN_KTS_ANNOTATION_FQ_NAMES) return null

        return annotationShortName
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtAnnotationEntry,
        context: String,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val shortFileName = FileUtil.getNameWithoutExtension(element.containingKtFile.name)
        val fix = RenameScriptToMainKtsQuickFix(shortFileName)
        return createProblemDescriptor(
            element,
            rangeInElement,
            KotlinBaseScriptingBundle.message("inspection.main.kts.annotation.in.plain.script.problem", context),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            onTheFly,
            fix,
        )
    }
}

private class RenameScriptToMainKtsQuickFix(
    private val shortFileName: String,
) : ModCommandQuickFix() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBaseScriptingBundle.message("inspection.main.kts.annotation.rename.fix.family")

    override fun getName(): @IntentionName String =
        KotlinBaseScriptingBundle.message("inspection.main.kts.annotation.rename.fix.name", shortFileName)

    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
        val virtualFile = descriptor.startElement?.containingFile?.originalFile?.virtualFile ?: return ModCommand.nop()
        val parent = virtualFile.parent ?: return ModCommand.nop()
        val newFileName = "$shortFileName.main.kts"

        val existing = parent.findChild(newFileName)
        if (existing != null && existing != virtualFile) return ModCommand.nop()

        return ModMoveFile(virtualFile, FutureVirtualFile(parent, newFileName, virtualFile.fileType))
    }
}
