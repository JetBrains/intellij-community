// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface KotlinScriptEntity : WorkspaceEntity {
    val virtualFileUrl: VirtualFileUrl
    val dependencies: List<KotlinScriptLibraryEntityId>
    val configuration: ScriptCompilationConfigurationEntity?
    val sdkId: SdkId?

    @Suppress("RemoveExplicitTypeArguments")
    val relatedModuleIds: List<ModuleId>
        @Default get() = listOf<ModuleId>()

    @Suppress("RemoveExplicitTypeArguments")
    val reports: List<ScriptDiagnosticData>
        @Default get() = listOf<ScriptDiagnosticData>()
}
