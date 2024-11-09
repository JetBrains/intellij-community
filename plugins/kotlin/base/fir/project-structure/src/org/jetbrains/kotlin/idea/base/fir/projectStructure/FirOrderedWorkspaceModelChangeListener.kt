// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.FirIdeModuleStateModificationService
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

/**
 * [FirOrderedWorkspaceModelChangeListener] delegates to other components which process workspace model change events in a pre-defined
 * order.
 */
internal class FirOrderedWorkspaceModelChangeListener(private val project: Project) : WorkspaceModelChangeListener {
    override fun beforeChanged(event: VersionedStorageChange) {
        FirIdeModuleStateModificationService.getInstance(project).beforeWorkspaceModelChanged(event)

        // `LibraryInfoCache` is ordered last so that other listeners can still access `LibraryInfo`s before they might be invalidated in
        // the context of the current event. If `LibraryInfoCache`'s invalidation was ordered first, the following hypothetical situation
        // might occur:
        //
        //  1. A root is added to some library `L` and `FirOrderedWorkspaceModelChangeListener.beforeChanged` is called.
        //  2. `LibraryInfoCache` receives the "before change" event and removes the library info for `L` from its cache.
        //  3. `FirIdeModuleStateModificationService` processes the same library change event. To publish a module state modification event
        //     for the `KaModule` of `L`, `FirIdeModuleStateModificationService` accesses `LibraryInfoCache` to get `L`'s library infos.
        //  4. Because `LibraryInfoCache` has already thrown away the library info for `L`, the cache returns a new `LibraryInfo`.
        //  5. `LibraryInfo`s are referentially equal, so `FirIdeModuleStateModificationService` publishes a module state modification event
        //     for a `KaModule` based on a *new* `LibraryInfo`.
        //  6. The session invalidation service receives the module state modification event for the `KaModule`, but doesn't invalidate any
        //     sessions, because the new `LibraryInfo` key doesn't match the old `LibraryInfo` key still used in session hash maps.
        //
        // In the worst case, this will cause session invalidation to skip invalidation for all dependent modules as well, because it might
        // think that `L` has already been invalidated.
        @OptIn(K1ModeProjectStructureApi::class)
        project.serviceIfCreated<LibraryInfoCache>()?.beforeWorkspaceModelChanged(event)
    }
}
