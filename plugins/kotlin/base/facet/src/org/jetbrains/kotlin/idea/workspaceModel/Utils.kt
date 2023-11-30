// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import org.jetbrains.kotlin.config.CompilerSettings

fun CompilerSettingsData.toCompilerSettings(): CompilerSettings =
    CompilerSettings().also {
        it.additionalArguments = this.additionalArguments
        it.scriptTemplates = this.scriptTemplates
        it.scriptTemplatesClasspath = this.scriptTemplatesClasspath
        it.copyJsLibraryFiles = this.copyJsLibraryFiles
        it.outputDirectoryForJsLibraryFiles = this.outputDirectoryForJsLibraryFiles
    }

fun CompilerSettings?.toCompilerSettingsData(): CompilerSettingsData =
    CompilerSettingsData(
        this?.additionalArguments ?: "",
        this?.scriptTemplates ?: "",
        this?.scriptTemplatesClasspath ?: "",
        this?.copyJsLibraryFiles ?: true,
        this?.outputDirectoryForJsLibraryFiles ?: "lib"
    )