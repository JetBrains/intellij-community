// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.platform.backend.workspace.WorkspaceModelCacheVersion

class KotlinScriptWorkspaceModelCacheVersion: WorkspaceModelCacheVersion {
    override fun getId(): String = "KotlinScript"

    override fun getVersion(): String = "2"
}