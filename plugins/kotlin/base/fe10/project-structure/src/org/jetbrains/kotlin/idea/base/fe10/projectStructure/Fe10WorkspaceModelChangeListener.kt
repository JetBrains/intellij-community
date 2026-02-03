// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.projectStructure

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache

internal class Fe10WorkspaceModelChangeListener(private val project: Project) : WorkspaceModelChangeListener {
    override fun beforeChanged(event: VersionedStorageChange) {
        project.serviceIfCreated<LibraryInfoCache>()?.beforeWorkspaceModelChanged(event)
    }
}
