// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.configuration

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import kotlin.script.experimental.intellij.IdeScriptConfigurationControlFacade

class DefaultIdeScriptingConfigurationFacade : IdeScriptConfigurationControlFacade {
    override fun reloadScriptConfiguration(scriptFile: PsiFile, updateEditorWithoutNotification: Boolean) {
      (scriptFile as? KtFile) ?: error("Should be called with script KtFile, but called with $scriptFile")
        val project = scriptFile.project.takeIf { !it.isDisposed } ?: return
        DefaultScriptingSupport.getInstance(project)
            .ensureUpToDatedConfigurationSuggested(
                scriptFile,
                skipNotification = updateEditorWithoutNotification,
                forceSync = true
            )
    }
}