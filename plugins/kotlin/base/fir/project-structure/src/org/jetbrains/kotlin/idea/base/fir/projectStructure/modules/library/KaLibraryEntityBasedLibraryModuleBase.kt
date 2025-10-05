// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface

abstract class KaLibraryEntityBasedLibraryModuleBase : KaEntityBasedLibraryModuleBase<LibraryEntity, LibraryId>() {
    val library: LibraryEx
        get() = entity.findLibraryBridge(currentSnapshot) as LibraryEx?
            ?: error("Could not find Library $entityId")

    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        library.rootProvider.getFiles(OrderRootType.CLASSES).toList()
    }

    override val libraryName: String get() = entity.name

    @KaPlatformInterface
    override val isSdk: Boolean get() = false
}
