// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

interface GradleBuildScriptSupport {
    companion object {
        val EP_NAME: ExtensionPointName<GradleBuildScriptSupport> =
            ExtensionPointName.create("org.jetbrains.kotlin.idea.gradleBuildScriptSupport")

        fun findManipulator(file: PsiFile, preferNewSyntax: Boolean = true): GradleBuildScriptManipulator<*>? {
            for (extension in EP_NAME.extensionList) {
                return extension.createManipulator(file, preferNewSyntax) ?: continue
            }

            return null
        }

        fun getManipulator(file: PsiFile, preferNewSyntax: Boolean = true): GradleBuildScriptManipulator<*> {
            return findManipulator(file, preferNewSyntax) ?: error("Unknown build script file type (${file::class.qualifiedName})!")
        }
    }

    fun createManipulator(file: PsiFile, preferNewSyntax: Boolean): GradleBuildScriptManipulator<*>?

    fun createScriptBuilder(file: PsiFile): SettingsScriptBuilder<*>?
}