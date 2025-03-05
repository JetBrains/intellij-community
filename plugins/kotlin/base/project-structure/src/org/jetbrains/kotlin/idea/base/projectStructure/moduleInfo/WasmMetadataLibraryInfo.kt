// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmJsLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmWasiLibraryKind
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import java.lang.IllegalStateException

@K1ModeProjectStructureApi
class WasmMetadataLibraryInfo internal constructor(project: Project, library: LibraryEx) : LibraryInfo(project, library) {
    override val platform: TargetPlatform
        get() = when (library.kind) {
            is KotlinWasmJsLibraryKind -> WasmPlatforms.wasmJs
            is KotlinWasmWasiLibraryKind -> WasmPlatforms.wasmWasi
            else -> throw IllegalStateException("Unexpected library kind '${library.kind}' for $library")
        }
}