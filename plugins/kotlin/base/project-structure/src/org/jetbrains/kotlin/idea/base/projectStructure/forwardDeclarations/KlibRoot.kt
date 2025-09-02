// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.kotlin.idea.base.util.asKotlinLogger
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.ToolingSingleFileKlibResolveStrategy
import org.jetbrains.kotlin.library.resolveSingleFileKlib

internal data class KLibRoot(
    val library: LibraryEx,
    val libraryRoot: String,
) {
    val resolvedKotlinLibrary: KotlinLibrary by lazy {
        resolveSingleFileKlib(
            libraryFile = File(libraryRoot),
            logger = LOG.asKotlinLogger(),
            strategy = ToolingSingleFileKlibResolveStrategy
        )
    }
}

private val LOG = logger<KLibRoot>()