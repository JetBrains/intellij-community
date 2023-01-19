// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.changePackage

import com.intellij.CommonBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiManager
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.AutocreatingSingleSourceRootMoveDestination
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil
import com.intellij.refactoring.util.RefactoringMessageUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.findExistingNonGeneratedKotlinSourceRootFiles
import org.jetbrains.kotlin.idea.core.getFqNameByDirectory
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.packageMatchesDirectoryOrImplicit
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.hasIdentifiersOnly
import org.jetbrains.kotlin.idea.refactoring.isInjectedFragment
import org.jetbrains.kotlin.idea.roots.getSuitableDestinationSourceRoots
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.packageDirectiveVisitor
import org.jetbrains.kotlin.psi.psiUtil.startOffset
class PackageDirectoryMismatchInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = packageDirectiveVisitor(fun(directive: KtPackageDirective) {
        val file = directive.containingKtFile
        if (file.textLength == 0 || file.isInjectedFragment || file.packageMatchesDirectoryOrImplicit()) return

        val fixes = mutableListOf<LocalQuickFix>()
        val qualifiedName = directive.qualifiedName
        val dirName = if (qualifiedName.isEmpty())
            KotlinBundle.message("fix.move.file.to.package.dir.name.text")
        else
            "'${qualifiedName.replace('.', '/')}'"

        fixes += MoveFileToPackageFix(dirName)
        val fqNameByDirectory = file.getFqNameByDirectory()
        when {
            fqNameByDirectory.isRoot ->
                fixes += ChangePackageFix(KotlinBundle.message("fix.move.file.to.package.dir.name.text"), fqNameByDirectory)
            fqNameByDirectory.hasIdentifiersOnly() ->
                fixes += ChangePackageFix("'${fqNameByDirectory.asString()}'", fqNameByDirectory)
        }
        val fqNameWithImplicitPrefix = file.parent?.getFqNameWithImplicitPrefix()
        if (fqNameWithImplicitPrefix != null && fqNameWithImplicitPrefix != fqNameByDirectory) {
            fixes += ChangePackageFix("'${fqNameWithImplicitPrefix.asString()}'", fqNameWithImplicitPrefix)
        }

        val textRange = if (directive.textLength != 0) directive.textRange else file.declarations.firstOrNull()?.let {
            TextRange.from(it.startOffset, 1)
        }
        holder.registerProblem(
            file,
            textRange,
            KotlinBundle.message("text.package.directive.dont.match.file.location"),
            *fixes.toTypedArray()
        )
    })

    private class MoveFileToPackageFix(val dirName: String) : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("fix.move.file.to.package.family")

        override fun getName() = KotlinBundle.message("fix.move.file.to.package.text", dirName)

        override fun startInWriteAction() = false

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as? KtFile ?: return
            val directive = file.packageDirective ?: return

            val sourceRoots = file.module?.findExistingNonGeneratedKotlinSourceRootFiles() ?: getSuitableDestinationSourceRoots(project)
            val packageWrapper = PackageWrapper(PsiManager.getInstance(project), directive.qualifiedName)
            val fileToMove = directive.containingFile
            val chosenRoot =
                sourceRoots.singleOrNull()
                    ?: CommonMoveClassesOrPackagesUtil.chooseSourceRoot(packageWrapper, sourceRoots, fileToMove.containingDirectory)
                    ?: return

            val targetDirFactory = AutocreatingSingleSourceRootMoveDestination(packageWrapper, chosenRoot)
            targetDirFactory.verify(fileToMove)?.let {
                Messages.showMessageDialog(project, it, CommonBundle.getErrorTitle(), Messages.getErrorIcon())
                return
            }
            val targetDirectory = runWriteAction {
                targetDirFactory.getTargetDirectory(fileToMove)
            } ?: return

            RefactoringMessageUtil.checkCanCreateFile(targetDirectory, file.name)?.let {
                Messages.showMessageDialog(project, it, CommonBundle.getErrorTitle(), Messages.getErrorIcon())
                return
            }

            runWriteAction {
                MoveFilesOrDirectoriesUtil.doMoveFile(file, targetDirectory)
            }
        }
    }

    private class ChangePackageFix(val packageName: String, val packageFqName: FqName) : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("fix.change.package.family")

        override fun getName() = KotlinBundle.message("fix.change.package.text", packageName)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as? KtFile ?: return
            KotlinChangePackageRefactoring(file).run(packageFqName)
        }

        override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
            val file = previewDescriptor.psiElement as? KtFile ?: return IntentionPreviewInfo.EMPTY
            val packageDirective = file.packageDirective ?: return IntentionPreviewInfo.EMPTY
            packageDirective.fqName = packageFqName
            return IntentionPreviewInfo.DIFF
        }
    }
}