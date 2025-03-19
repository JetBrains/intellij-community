// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.changePackage

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.MoveKotlinDeclarationsProcessor
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

class KotlinChangePackageRefactoring(val file: KtFile) {
    private val project = file.project

    fun run(newFqName: FqName) {
        val packageDirective = file.packageDirective ?: return
        val currentFqName = packageDirective.fqName

        val declarationProcessor = MoveKotlinDeclarationsProcessor(
            MoveDeclarationsDescriptor(
              project = project,
              moveSource = KotlinMoveSource(file),
              moveTarget = KotlinMoveTarget.Directory(newFqName, file.containingDirectory!!.virtualFile),
              delegate = KotlinMoveDeclarationDelegate.TopLevel,
              searchInCommentsAndStrings = KotlinRefactoringSettings.instance.MOVE_SEARCH_IN_COMMENTS,
              searchInNonCode = KotlinRefactoringSettings.instance.MOVE_SEARCH_FOR_TEXT,
            )
        )

        val declarationUsages = project.runSynchronouslyWithProgress(RefactoringBundle.message("progress.text"), true) {
            runReadAction {
                declarationProcessor.findUsages().toList()
            }
        } ?: return
        val changeInfo = MoveContainerChangeInfo(MoveContainerInfo.Package(currentFqName), MoveContainerInfo.Package(newFqName))
        val internalUsages = file.getInternalReferencesToUpdateOnPackageNameChange(changeInfo)

        project.executeCommand(KotlinBundle.message("text.change.file.package.to.0", newFqName)) {
            runWriteAction {
                packageDirective.fqName = newFqName.quoteIfNeeded()
                postProcessMoveUsages(internalUsages)
                performDelayedRefactoringRequests(project)
            }
            declarationProcessor.execute(declarationUsages)
        }

    }
}
