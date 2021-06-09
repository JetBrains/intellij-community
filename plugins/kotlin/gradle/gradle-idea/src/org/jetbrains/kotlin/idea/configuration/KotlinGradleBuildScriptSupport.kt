// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.extensions.gradle.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.extensions.gradle.GradleVersionProvider
import org.jetbrains.kotlin.idea.extensions.gradle.SettingsScriptBuilder
import org.jetbrains.kotlin.psi.KtFile

class KotlinGradleBuildScriptSupport : GradleBuildScriptSupport {
    override fun createManipulator(
        file: PsiFile,
        preferNewSyntax: Boolean,
        versionProvider: GradleVersionProvider
    ): KotlinBuildScriptManipulator? {
        if (file !is KtFile) {
            return null
        }

        return KotlinBuildScriptManipulator(file, preferNewSyntax, versionProvider)
    }

    override fun createScriptBuilder(file: PsiFile): SettingsScriptBuilder<*>? {
        return if (file is KtFile) KotlinSettingsScriptBuilder(file) else null
    }
}