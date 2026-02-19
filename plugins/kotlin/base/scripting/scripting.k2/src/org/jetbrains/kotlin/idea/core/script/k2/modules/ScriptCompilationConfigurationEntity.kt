// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

interface ScriptCompilationConfigurationEntity : WorkspaceEntityWithSymbolicId {
    val data: ByteArray
    val identity: ScriptCompilationConfigurationIdentity

    override val symbolicId: ScriptCompilationConfigurationIdentity
        get() = identity
}

data class ScriptCompilationConfigurationIdentity(val hash: Long, val tag: Byte) : SymbolicEntityId<ScriptCompilationConfigurationEntity> {
    override val presentableName: @NlsSafe String
        get() = toString()
}