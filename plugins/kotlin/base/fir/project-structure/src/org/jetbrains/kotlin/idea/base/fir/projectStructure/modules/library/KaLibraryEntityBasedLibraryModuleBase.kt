// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface

abstract class KaLibraryEntityBasedLibraryModuleBase : KaEntityBasedLibraryModuleBase<LibraryEntity, LibraryId>() {
    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        computeRoots(LibraryRootTypeId.COMPILED)
    }

    override val libraryName: String get() = entity.name

    @KaPlatformInterface
    override val isSdk: Boolean get() = false

    internal fun computeRoots(rootType: LibraryRootTypeId): List<VirtualFile> {
        return entity.roots
            .filter { it.type == rootType }
            .mapNotNull { it.url.virtualFile }
    }
}
