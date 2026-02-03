// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter

@ApiStatus.Internal
class RenameParameterToMatchOverriddenMethodFix(
    parameter: KtParameter,
    private val newName: Name
) : KotlinQuickFixAction<KtParameter>(parameter) {

    override fun getFamilyName(): String = KotlinBundle.message("rename.identifier.fix.text")

    override fun getText(): String = KotlinBundle.message("rename.parameter.to.match.overridden.method")

    override fun startInWriteAction(): Boolean = false

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        RenameProcessor(
            project,
            element ?: return,
            newName.toString(),
            /* isSearchInComments =*/ false,
            /* isSearchTextOccurrences =*/ false
        ).run()
    }
}
