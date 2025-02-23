// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile

class MoveToKotlinFileProcessor @JvmOverloads constructor(
    project: Project,
    private val sourceFile: KtFile,
    private val targetDirectory: PsiDirectory,
    private val targetFileName: String,
    searchInComments: Boolean,
    searchInNonJavaFiles: Boolean,
    moveCallback: MoveCallback?,
    prepareSuccessfulCallback: Runnable = EmptyRunnable.INSTANCE,
    private val throwOnConflicts: Boolean = false
) : MoveFilesOrDirectoriesProcessor(
    project,
    arrayOf(sourceFile),
    targetDirectory,
    true,
    searchInComments,
    searchInNonJavaFiles,
    moveCallback,
    prepareSuccessfulCallback
) {
    override fun getCommandName() = KotlinBundle.message("text.move.file.0", sourceFile.name)

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return MoveFilesWithDeclarationsViewDescriptor(arrayOf(sourceFile), targetDirectory)
    }

    protected override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val (conflicts, usages) = MoveConflictUsages.preprocess(refUsages)
        return showConflicts(conflicts, usages)
    }

    override fun showConflicts(conflicts: MultiMap<PsiElement, String>, usages: Array<out UsageInfo>?): Boolean {
        if (throwOnConflicts && !conflicts.isEmpty) throw MoveConflictsFoundException()
        return super.showConflicts(conflicts, usages)
    }

    private fun renameFileTemporarilyIfNeeded() {
        if (targetDirectory.findFile(sourceFile.name) == null) return

        val containingDirectory = sourceFile.containingDirectory ?: return

        val temporaryName = UniqueNameGenerator.generateUniqueName("temp", "", ".kt") {
            containingDirectory.findFile(it) == null
        }
        sourceFile.name = temporaryName
    }

    override fun performRefactoring(usages: Array<UsageInfo>) {
        renameFileTemporarilyIfNeeded()
        super.performRefactoring(usages)
        sourceFile.name = targetFileName
    }
}
