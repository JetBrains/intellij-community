// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.FirIdeModuleStateModificationService

/**
 * [FirOrderedWorkspaceModelChangeListener] delegates to other components which process workspace model change events in a pre-defined
 * order.
 */
internal class FirOrderedWorkspaceModelChangeListener(private val project: Project) : WorkspaceModelChangeListener {
    override fun beforeChanged(event: VersionedStorageChange) {
        FirIdeModuleStateModificationService.getInstance(project).beforeWorkspaceModelChanged(event)
    }
}
