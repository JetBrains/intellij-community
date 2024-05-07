// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiModuleTest
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractHighLevelQuickFixMultiModuleTest : AbstractQuickFixMultiModuleTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun findAfterFile(editedFile: KtFile): PsiFile? {
        val firAfter = editedFile.containingDirectory?.findFile(editedFile.name + ".fir.after")
        if (firAfter != null) return firAfter
        return editedFile.containingDirectory?.findFile(editedFile.name + ".after")
    }
}