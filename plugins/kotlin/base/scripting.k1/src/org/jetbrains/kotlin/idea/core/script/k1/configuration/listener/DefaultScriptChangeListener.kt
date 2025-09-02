// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.configuration.listener

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.scripting.definitions.isNonScript

open class DefaultScriptChangeListener(project: Project) : ScriptChangeListener(project) {
    override fun editorActivated(vFile: VirtualFile) {
        val file = getAnalyzableKtFileForScript(vFile) ?: return
        default.ensureUpToDatedConfigurationSuggested(file)
    }

    override fun documentChanged(vFile: VirtualFile) {
        val file = getAnalyzableKtFileForScript(vFile) ?: return
        default.ensureUpToDatedConfigurationSuggested(file)
    }

    override fun isApplicable(vFile: VirtualFile): Boolean {
        return vFile.isValid && !vFile.isNonScript()
    }
}
