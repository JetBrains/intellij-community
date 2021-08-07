// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.extensions.gradle

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

interface GradleBuildScriptSupport {
    companion object {
        val EP_NAME: ExtensionPointName<GradleBuildScriptSupport> =
            ExtensionPointName.create("org.jetbrains.kotlin.idea.gradleBuildScriptSupport")
    }

    fun createManipulator(
        file: PsiFile,
        preferNewSyntax: Boolean,
        versionProvider: GradleVersionProvider
    ): GradleBuildScriptManipulator<*>?

    fun createScriptBuilder(file: PsiFile): SettingsScriptBuilder<*>?
}