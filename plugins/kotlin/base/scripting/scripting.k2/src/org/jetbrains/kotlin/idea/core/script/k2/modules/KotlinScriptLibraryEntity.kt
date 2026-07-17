// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface KotlinScriptLibraryEntity : WorkspaceEntityWithSymbolicId {
    /**
     * Provider-specific identity scope.
     *
     * Libraries with the same scope and class roots intentionally share one entity in the workspace model.
     * Providers that contribute script models for projects inside a workspace should use a scope that identifies
     * the owning project, so equal library roots from different projects are not merged accidentally.
     */
    val scope: @NlsSafe String

    val classes: List<VirtualFileUrl>

    val usedInScripts: Set<VirtualFileUrl>

    @Suppress("RemoveExplicitTypeArguments")
    val sources: Set<VirtualFileUrl>
        @Default get() = setOf<VirtualFileUrl>()

    override val symbolicId: KotlinScriptLibraryEntityId
        get() = KotlinScriptLibraryEntityId(scope, classes)
}

data class KotlinScriptLibraryEntityId(
    val scope: @NlsSafe String,
    val classes: List<VirtualFileUrl>,
) : SymbolicEntityId<KotlinScriptLibraryEntity> {
    override val presentableName: @NlsSafe String
        get() = kotlinScriptLibraryPresentableName(scope, classes)

    override fun toString(): String = presentableName
}

fun kotlinScriptLibraryPresentableName(scope: @NlsSafe String, classUrls: Collection<VirtualFileUrl>): @NlsSafe String =
    classUrls.joinToString(prefix = "$scope: ") { it.presentableUrl }
