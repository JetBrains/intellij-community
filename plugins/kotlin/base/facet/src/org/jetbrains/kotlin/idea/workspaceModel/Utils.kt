// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import org.jetbrains.kotlin.cli.common.arguments.Freezable
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.CompilerSettings.Companion.DEFAULT_OUTPUT_DIRECTORY
import org.jetbrains.kotlin.config.copyCompilerSettings

class ObservableCompilerSettings(val updateEntity: (CompilerSettings) -> Unit) : CompilerSettings() {
    override var additionalArguments: String = DEFAULT_ADDITIONAL_ARGUMENTS
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }

    override var scriptTemplates: String = ""
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }
    override var scriptTemplatesClasspath: String = ""
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }
    override var copyJsLibraryFiles = true
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }
    override var outputDirectoryForJsLibraryFiles: String = DEFAULT_OUTPUT_DIRECTORY
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }

    override fun copyOf(): Freezable = copyCompilerSettings(this, ObservableCompilerSettings(updateEntity))
}

fun CompilerSettingsData.toCompilerSettings(notifier: (CompilerSettings) -> Unit): CompilerSettings =
    ObservableCompilerSettings(notifier).also {
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
        this?.outputDirectoryForJsLibraryFiles ?: "lib",
        true
    )