// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.shared.KotlinScriptCachedProblemHighlightFilter
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.psi.KtFile

internal class ScriptProblemHighlightFilter : KotlinScriptCachedProblemHighlightFilter() {
    override fun shouldHighlightScript(file: KtFile): Boolean {
        val workspaceModel = file.project.workspaceModel

        val fileUrlManager = workspaceModel.getVirtualFileUrlManager()
        return workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
            .findEntitiesByUrl(file.alwaysVirtualFile.toVirtualFileUrl(fileUrlManager))
            .filterIsInstance<KotlinScriptEntity>().any()
    }
}
