// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.loader

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.shared.CachedConfigurationInputs
import org.jetbrains.kotlin.psi.KtFile

open class FileContentsDependentConfigurationLoader(project: Project) : DefaultScriptConfigurationLoader(project) {
    override fun getInputsStamp(virtualFile: VirtualFile, file: KtFile): CachedConfigurationInputs {
        return CachedConfigurationInputs.SourceContentsStamp.get(virtualFile)
    }
}