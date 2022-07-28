// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.coroutines

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class RenameToFix(val newName: String) : LocalQuickFix {
    override fun getName(): String = KotlinBundle.message("rename.to.fix.text", newName)

    override fun getFamilyName(): String = name

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val function = descriptor.psiElement.getNonStrictParentOfType<KtFunction>() ?: return
        RenameProcessor(project, function, newName, false, false).run()
    }
}