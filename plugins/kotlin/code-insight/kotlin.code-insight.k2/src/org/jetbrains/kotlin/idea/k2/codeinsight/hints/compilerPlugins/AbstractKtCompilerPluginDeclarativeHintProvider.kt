// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.AbstractKtInlayHintsProvider
import org.jetbrains.kotlin.psi.KtFile

internal abstract class AbstractKtCompilerPluginDeclarativeHintProvider : AbstractKtInlayHintsProvider() {
    override fun shouldShowForFile(ktFile: KtFile, project: Project): Boolean {
        return ktFile.fileCanBeAffectedByCompilerPlugins(project)
    }
}
