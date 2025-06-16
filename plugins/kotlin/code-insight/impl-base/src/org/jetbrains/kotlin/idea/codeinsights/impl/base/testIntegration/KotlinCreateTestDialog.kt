// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiPackage
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.refactoring.util.RefactoringMessageUtil
import com.intellij.testIntegration.createTest.CreateTestDialog
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.util.Locale.getDefault

class KotlinCreateTestDialog(
    project: Project,
    @Nls title: String,
    sourceElement: KtElement?,
    targetClass: PsiClass?,
    targetPackage: PsiPackage?,
    targetModule: Module
) : CreateTestDialog(project, title, targetClass, targetPackage, targetModule) {
    var explicitClassName: String? = null

    init {
        suggestTestClassName(targetClass, sourceElement)?.let { className = it }
    }

    override fun getClassName(): String = explicitClassName ?: super.className

    override fun checkCanCreateClass(): String? =
        RefactoringMessageUtil.checkCanCreateClass(
            myTargetDirectory,
            getClassName(),
            if (KotlinPluginModeProvider.isK1Mode()) {
                JavaFileType.INSTANCE
            } else {
                KotlinFileType.INSTANCE
            }
        )

    override fun suggestTestClassName(targetClass: PsiClass?): String? =
        targetClass?.let { suggestTestClassName(it, null) }

    private fun suggestTestClassName(targetClass: PsiClass?, sourceElement: KtElement?): String? {
        val containingFile = targetClass?.containingFile ?: return null
        val customSettings = JavaCodeStyleSettings.getInstance(containingFile)
        val prefix = customSettings.TEST_NAME_PREFIX
        val suffix = customSettings.TEST_NAME_SUFFIX
        val name =
            if (sourceElement is KtNamedFunction && sourceElement.isTopLevel || sourceElement is KtProperty && sourceElement.isTopLevel) {
                sourceElement.name?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
            } else {
                targetClass?.name?.removeSuffixIfPresent("Kt")
            }
        return name?.let { prefix + name + suffix }
    }
}
