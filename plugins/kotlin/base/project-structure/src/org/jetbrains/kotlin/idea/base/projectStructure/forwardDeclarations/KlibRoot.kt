// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.loader.KlibLoader
import org.jetbrains.kotlin.library.loader.reportLoadingProblemsIfAny

internal data class KLibRoot(
    val libraryRoot: String,
) {
    val resolvedKotlinLibrary: KotlinLibrary? by lazy {
        val klibLoadingResult = KlibLoader { libraryPaths(libraryRoot) }.load()
        klibLoadingResult.reportLoadingProblemsIfAny { _, message -> LOG.warn(message) }
        klibLoadingResult.librariesStdlibFirst.singleOrNull()
    }
}

private val LOG = logger<KLibRoot>()