// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class GenerateFunctionFix(private val functionDefinitionText: String, private val bodyText: String) : LocalQuickFix {
    override fun getFamilyName() = name

    override fun startInWriteAction() = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.psiElement)) return
        val klass = descriptor.psiElement.getNonStrictParentOfType<KtClass>() ?: return
        val factory = KtPsiFactory(klass)

        runWriteAction {
            val function = factory.createFunction(functionDefinitionText)
            shortenReferences(function)
            if (bodyText.isNotEmpty()) function.bodyExpression?.replace(factory.createBlock(bodyText))
            klass.addDeclaration(function)
        }
    }
}