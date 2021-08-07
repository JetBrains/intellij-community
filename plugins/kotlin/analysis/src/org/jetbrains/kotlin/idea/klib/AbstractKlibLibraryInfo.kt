// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.klib

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.caches.project.LibraryInfo
import org.jetbrains.kotlin.idea.util.IJLoggerAdapter
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.library.*
import org.jetbrains.kotlin.platform.TargetPlatform

abstract class AbstractKlibLibraryInfo(project: Project, library: Library, val libraryRoot: String) : LibraryInfo(project, library) {

    val resolvedKotlinLibrary: KotlinLibrary = resolveSingleFileKlib(
        libraryFile = File(libraryRoot),
        logger = LOG,
        strategy = ToolingSingleFileKlibResolveStrategy
    )

    val compatibilityInfo: KlibCompatibilityInfo by lazy { resolvedKotlinLibrary.getCompatibilityInfo() }

    final override fun getLibraryRoots() = listOf(libraryRoot)

    abstract override val platform: TargetPlatform // must override

    val uniqueName: String? by lazy { resolvedKotlinLibrary.safeRead(null) { uniqueName } }

    val isInterop: Boolean by lazy { resolvedKotlinLibrary.safeRead(false) { isInterop } }

    companion object {
        private val LOG = IJLoggerAdapter.getInstance(AbstractKlibLibraryInfo::class.java)
    }
}
