// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.CommonBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.SingleFileSourcesTracker
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiManager
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.AutocreatingSingleSourceRootMoveDestination
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil
import com.intellij.refactoring.util.RefactoringMessageUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.hasIdentifiersOnly
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.findExistingNonGeneratedKotlinSourceRootFiles
import org.jetbrains.kotlin.idea.core.getFqNameByDirectory
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.packageMatchesDirectoryOrImplicit
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2ChangePackageDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2ChangePackageRefactoringProcessor
import org.jetbrains.kotlin.idea.roots.getSuitableDestinationSourceRoots
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class PackageDirectoryMismatchInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitPackageDirective(directive: KtPackageDirective) {
            val project = holder.project
            val file = directive.containingKtFile
            if (file.textLength == 0
                || InjectedLanguageManager.getInstance(project).isInjectedFragment(file)
                || file.packageMatchesDirectoryOrImplicit()) return

            val fixes = mutableListOf<LocalQuickFix>()
            val qualifiedName = directive.qualifiedName
            val dirName = if (qualifiedName.isEmpty())
              KotlinBundle.message("fix.move.file.to.package.dir.name.text")
            else
                "'${qualifiedName.replace('.', '/')}'"

            val singleFileSourcesTracker = SingleFileSourcesTracker.getInstance(file.project)
            val isSingleFileSource = singleFileSourcesTracker.isSingleFileSource(file.virtualFile)
            if (!isSingleFileSource) fixes += MoveFileToPackageFix(dirName)
            val fqNameByDirectory = file.getFqNameByDirectory()
            when {
                fqNameByDirectory.isRoot ->
                    fixes += ChangePackageFix(KotlinBundle.message("fix.move.file.to.package.dir.name.text"), fqNameByDirectory)

                fqNameByDirectory.hasIdentifiersOnly() ->
                    fixes += ChangePackageFix("'${fqNameByDirectory.asString()}'", fqNameByDirectory)
            }
            val fqNameWithImplicitPrefix = file.parent?.getFqNameWithImplicitPrefix()
            if (!isSingleFileSource && fqNameWithImplicitPrefix != null && fqNameWithImplicitPrefix != fqNameByDirectory) {
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
        }
    }

    private class MoveFileToPackageFix(val dirName: String) : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("fix.move.file.to.package.family")

        override fun getName() = KotlinBundle.message("fix.move.file.to.package.text", dirName)

        override fun startInWriteAction() = false

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as? KtFile ?: return
            val directive = file.packageDirective ?: return

            val sourceRoots = file.module?.findExistingNonGeneratedKotlinSourceRootFiles()?.takeIf { it.isNotEmpty() } ?: getSuitableDestinationSourceRoots(project)
            val packageWrapper = PackageWrapper(PsiManager.getInstance(project), directive.qualifiedName)
            val fileToMove = directive.containingFile
            val chosenRoot = sourceRoots.singleOrNull()
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

        override fun startInWriteAction(): Boolean = false

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val file = descriptor.psiElement as? KtFile ?: return
            val changePkgDescriptor = K2ChangePackageDescriptor(file.project, setOf(file), packageFqName, false, false)
            K2ChangePackageRefactoringProcessor(changePkgDescriptor).run()
        }

        override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
            val file = previewDescriptor.psiElement as? KtFile ?: return IntentionPreviewInfo.EMPTY
            val packageDirective = file.packageDirective ?: return IntentionPreviewInfo.EMPTY
            packageDirective.fqName = packageFqName
            return IntentionPreviewInfo.DIFF
        }
    }
}