// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class GenerateFunctionFix(private val functionDefinitionText: String) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): @IntentionName String = name

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val klass = element.getNonStrictParentOfType<KtClass>() ?: return
        val function = KtPsiFactory.contextual(klass).createFunction(functionDefinitionText)
        shortenReferences(klass.addDeclaration(function))
    }
}